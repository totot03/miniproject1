#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_master_seed.py — 공공데이터 -> region / pharmacy / drug / app_user 마스터 시드 변환

docs/ROADMAP.md T-07 참고. 이 스크립트는 런타임 백엔드가 호출하는 것이 아니라,
개발자가 로컬에서 1회(또는 데이터 갱신 시 재실행) 돌려 V2__seed_master.sql 을
만들어내는 오프라인 도구다.

데이터 소스 (T-07 계획 문서에서 확정한 결정):
    - 약국   : 심평원 약국정보서비스 OpenAPI(getParmacyBasisList) — 좌표(XPos/YPos) 포함
    - 의약품 : data/drug_master.csv 수작업 목록 (30~50종)
    - 행정구역: 별도 데이터셋 없이 약국 응답의 시도/시군구 코드를 그대로 region 으로 구성

사용법:
    python build_master_seed.py                # HIRA API 를 호출해 처음부터 생성
    python build_master_seed.py --skip-fetch    # data/pharmacy_raw.csv 를 재사용 (서비스키 불필요)

환경변수 (.env, .env.example 참고):
    HIRA_SERVICE_KEY   공공데이터포털에서 발급받은 심평원 약국정보서비스 서비스키(Decoding 값)
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import math
import os
import random
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timezone, timedelta
from pathlib import Path
from typing import Optional

import bcrypt
import requests
from dotenv import load_dotenv

# -----------------------------------------------------------------------------
# 상수
# -----------------------------------------------------------------------------

SCRIPT_DIR = Path(__file__).resolve().parent
DATA_DIR = SCRIPT_DIR / "data"
DEFAULT_PHARMACY_RAW = DATA_DIR / "pharmacy_raw.csv"
DEFAULT_DRUG_CSV = DATA_DIR / "drug_master.csv"
DEFAULT_OUTPUT = (
    SCRIPT_DIR.parent.parent
    / "miniproject1-backend"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "migration"
    / "V2__seed_master.sql"
)

# HIRA 약국정보서비스 OpenAPI. data.go.kr id 15001673 (제공기관 B551182).
# ⚠️ "Parmacy"는 오탈자가 아니라 HIRA가 실제로 쓰는 오퍼레이션명이다.
# 아래 값들은 실제 API를 호출해 확인한 것이다 (병원정보서비스(15001698)의 hospInfoServicev2
# 와는 다른 제공기관 세그먼트이므로 별도 활용신청이 필요하다):
#   - sidoCd 는 법정동코드가 아니라 HIRA 자체 코드다. 서울=110000, 경기=310000
#     (getHospBasisList 로 실측 — sgguCd 도 앞 2자리에 이 sidoCd 접두어가 그대로 들어있어
#      전국 유일 키로 쓸 수 있다. 이 API도 같은 제공기관 스키마를 따를 것으로 보고 동일하게 둔다)
HIRA_ENDPOINT = "https://apis.data.go.kr/B551182/pharmacyInfoService/getParmacyBasisList"
SIDO_CODES = {"110000": "서울특별시", "310000": "경기도"}
# 이 API는 처음부터 약국만 응답할 가능성이 높지만(스키마 미확인), 혹시 clCdNm 필드가
# 있고 다른 종별이 섞여 온다면 방어적으로 필터링한다. 필드가 없으면 조건은 그냥 통과시킨다.
PHARMACY_CL_CD_NM = "약국"

# T-08 과 동일한 컨벤션 — 재실행해도 항상 같은 표본이 나오게 시드를 고정한다.
RANDOM_SEED = 20260915
SAMPLE_TARGET = 400
SAMPLE_MIN, SAMPLE_MAX = 300, 500

DRUG_CATEGORIES = {"해열진통", "소화제", "감기약", "연고", "소독약", "비타민", "기타"}

ADMIN_EMAIL = "admin@example.com"
ADMIN_PASSWORD = "Admin1234!"  # 시드 생성용 참고 평문. 실제 저장은 BCrypt 해시만.
DUMMY_USER_COUNT = 20
DUMMY_PASSWORD = "User1234!"
BCRYPT_ROUNDS = 10  # Spring Security BCryptPasswordEncoder() 기본 strength 와 동일


# -----------------------------------------------------------------------------
# 데이터 클래스
# -----------------------------------------------------------------------------


@dataclass
class PharmacyRow:
    # HIRA 약국정보서비스의 ykiho는 "요양기관기호" 라는 이름과 달리 실제로는 80자
    # 안팎의 인코딩된 문자열이다(실측: base64 디코드하면 "$481881#51#$1#$8#..." 형태의
    # 내부 구분자 인코딩). pharmacy.hira_code 컬럼은 VARCHAR(30)이라 원본을 그대로 넣으면
    # Flyway 마이그레이션이 "값이 너무 길다"로 실패한다. 원본은 여기 보관하고,
    # DB에 넣을 짧은 키는 hira_code 프로퍼티에서 해시로 만든다.
    ykiho_raw: str
    name: str
    address_road: Optional[str]
    phone: Optional[str]
    lat: float
    lng: float
    sido_code: str
    sido_name: str
    sigungu_code: str
    sigungu_name: str

    @property
    def hira_code(self) -> str:
        # 같은 ykiho_raw는 항상 같은 해시를 내므로 재실행해도 ON CONFLICT(hira_code)
        # 멱등성이 그대로 유지된다. 24자리 hex(96비트)라 이 규모(1만여 건)에서 충돌 위험은
        # 무시할 수준이다.
        return hashlib.sha256(self.ykiho_raw.encode("utf-8")).hexdigest()[:24]

    @property
    def region_code(self) -> str:
        # sgguCd 는 실측 결과 앞 2자리에 sidoCd 접두어를 이미 포함해 전국적으로 유일하다
        # (예: 서울 강남구 110001, 경기 가평군 310001). sidoCd 를 따로 이어붙이면
        # region.code VARCHAR(10)을 넘기므로 sgguCd 단독을 코드로 쓴다.
        return self.sigungu_code


@dataclass
class RegionRow:
    code: str
    sido: str
    sigungu: str
    center_lat: float
    center_lng: float


@dataclass
class DrugRow:
    item_seq: str
    name: str
    display_name: str
    maker: Optional[str]
    category: str
    form: Optional[str]
    package_unit: str
    otc_flag: bool
    base_price: Optional[int]


@dataclass
class UserRow:
    email: str
    password_hash: str
    nickname: str
    role: str = "USER"


# -----------------------------------------------------------------------------
# 1. 약국 수집 (HIRA OpenAPI)
# -----------------------------------------------------------------------------


def fetch_pharmacies_from_api(service_key: str) -> list[PharmacyRow]:
    """서울·경기 약국을 페이징 호출로 전부 받아온다."""
    rows: list[PharmacyRow] = []
    for sido_code, sido_name in SIDO_CODES.items():
        page = 1
        num_of_rows = 1000
        while True:
            params = {
                "serviceKey": service_key,
                "sidoCd": sido_code,
                "numOfRows": num_of_rows,
                "pageNo": page,
                "_type": "json",
            }
            resp = requests.get(HIRA_ENDPOINT, params=params, timeout=30)
            resp.raise_for_status()
            body = resp.json()["response"]["body"]
            items = body.get("items") or {}
            item_list = items.get("item") or []
            if isinstance(item_list, dict):  # 단일 결과일 때 dict 로 오는 API 특성 방어
                item_list = [item_list]

            for item in item_list:
                # 이 API는 이미 약국만 응답할 가능성이 높다. clCdNm 필드가 있고 값이
                # '약국'이 아닐 때만 걸러낸다 — 필드가 없다고 전부 버리면 안 된다.
                cl_cd_nm = item.get("clCdNm")
                if cl_cd_nm is not None and cl_cd_nm != PHARMACY_CL_CD_NM:
                    continue
                row = _to_pharmacy_row(item, sido_code, sido_name)
                if row is not None:
                    rows.append(row)

            total_count = int(body.get("totalCount", 0))
            print(f"  [{sido_name}] page={page} 누적수신={page * num_of_rows} / totalCount={total_count}")
            if page * num_of_rows >= total_count or not item_list:
                break
            page += 1
    return rows


def _to_pharmacy_row(item: dict, sido_code: str, sido_name: str) -> Optional[PharmacyRow]:
    # sidoCd/sgguCd 는 JSON에서 숫자로 온다(문자열이 아님) — str() 로 먼저 감싸지 않으면
    # int에 .strip()을 호출해 그대로 죽는다.
    ykiho_raw = str(item.get("ykiho") or "").strip()
    lat_raw, lng_raw = item.get("YPos"), item.get("XPos")
    sigungu_code = str(item.get("sgguCd") or "").strip()
    sigungu_name = str(item.get("sgguCdNm") or "").strip()

    if not ykiho_raw:
        return None  # ON CONFLICT(hira_code) 의 전제 — 코드 없는 행은 멱등성을 깨므로 제외
    if not sigungu_code or not sigungu_name:
        return None
    try:
        lat, lng = float(lat_raw), float(lng_raw)
    except (TypeError, ValueError):
        return None
    if lat == 0 or lng == 0:
        return None
    # 국내 좌표 범위 밖이면(위경도 뒤바뀜 등) 제외 — V1__init.sql 의 CHECK 제약과 동일 기준.
    if not (33 <= lat <= 39 and 124 <= lng <= 132):
        return None

    # 이 API는 문자열처럼 보이는 필드도 종종 숫자로 내려준다(관측: 전화번호가 하이픈 없이
    # 숫자만 있으면 telno가 int로 옴). 문자열 필드는 전부 str()로 감싸 방어한다.
    return PharmacyRow(
        ykiho_raw=ykiho_raw,
        name=str(item.get("yadmNm") or "").strip(),
        address_road=str(item.get("addr") or "").strip() or None,
        phone=str(item.get("telno") or "").strip() or None,
        lat=lat,
        lng=lng,
        sido_code=sido_code,
        sido_name=sido_name,
        sigungu_code=sigungu_code,
        sigungu_name=sigungu_name,
    )


def save_pharmacy_raw_csv(rows: list[PharmacyRow], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(
            ["ykiho_raw", "name", "address_road", "phone", "lat", "lng",
             "sido_code", "sido_name", "sigungu_code", "sigungu_name"]
        )
        for r in rows:
            writer.writerow(
                [r.ykiho_raw, r.name, r.address_road or "", r.phone or "",
                 r.lat, r.lng, r.sido_code, r.sido_name, r.sigungu_code, r.sigungu_name]
            )
    print(f"원본 약국 데이터 저장: {path} ({len(rows)}행)")


def load_pharmacy_raw_csv(path: Path) -> list[PharmacyRow]:
    if not path.exists():
        raise SystemExit(
            f"[오류] {path} 가 없다. --skip-fetch 는 이미 저장된 원본 데이터가 있을 때만 쓸 수 있다."
        )
    rows: list[PharmacyRow] = []
    with path.open(newline="", encoding="utf-8") as f:
        for rec in csv.DictReader(f):
            rows.append(
                PharmacyRow(
                    ykiho_raw=rec["ykiho_raw"],
                    name=rec["name"],
                    address_road=rec["address_road"] or None,
                    phone=rec["phone"] or None,
                    lat=float(rec["lat"]),
                    lng=float(rec["lng"]),
                    sido_code=rec["sido_code"],
                    sido_name=rec["sido_name"],
                    sigungu_code=rec["sigungu_code"],
                    sigungu_name=rec["sigungu_name"],
                )
            )
    print(f"캐시된 약국 데이터 로드: {path} ({len(rows)}행)")
    return rows


# -----------------------------------------------------------------------------
# 2. region 구성
# -----------------------------------------------------------------------------


def build_regions(pharmacies: list[PharmacyRow]) -> list[RegionRow]:
    buckets: dict[str, list[PharmacyRow]] = defaultdict(list)
    for p in pharmacies:
        buckets[p.region_code].append(p)

    regions: list[RegionRow] = []
    for code, members in buckets.items():
        lat_avg = sum(m.lat for m in members) / len(members)
        lng_avg = sum(m.lng for m in members) / len(members)
        regions.append(
            RegionRow(
                code=code,
                sido=members[0].sido_name,
                sigungu=members[0].sigungu_name,
                center_lat=lat_avg,
                center_lng=lng_avg,
            )
        )
    regions.sort(key=lambda r: r.code)
    return regions


# -----------------------------------------------------------------------------
# 3. pharmacy 샘플링 — 밀집 지역 우선으로 300~500건
# -----------------------------------------------------------------------------


def sample_pharmacies(pharmacies: list[PharmacyRow], target: int = SAMPLE_TARGET) -> list[PharmacyRow]:
    # hira_code 중복 제거 (같은 코드가 페이지 경계에서 두 번 잡히는 경우 방어)
    dedup: dict[str, PharmacyRow] = {}
    for p in pharmacies:
        dedup.setdefault(p.hira_code, p)
    unique = list(dedup.values())

    by_region: dict[str, list[PharmacyRow]] = defaultdict(list)
    for p in unique:
        by_region[p.region_code].append(p)

    # 약국이 많은 지역부터 누적 — "같은 행정구역에 몰리도록 뽑는다"를 데이터 주도로 만족시킨다.
    ordered_regions = sorted(by_region.items(), key=lambda kv: len(kv[1]), reverse=True)

    pool: list[PharmacyRow] = []
    for _, members in ordered_regions:
        pool.extend(members)
        if len(pool) >= target:
            break

    rng = random.Random(RANDOM_SEED)
    rng.shuffle(pool)
    sample = pool[:target] if len(pool) > target else pool

    if len(sample) < SAMPLE_MIN:
        raise SystemExit(
            f"[오류] 표본이 {len(sample)}건으로 최소 {SAMPLE_MIN}건에 못 미친다. "
            "SIDO_CODES 범위를 넓히거나 좌표 필터 기준을 재검토할 것."
        )
    return sample


# -----------------------------------------------------------------------------
# 4. drug 병합 (수작업 CSV 검증 + item_seq 부여)
# -----------------------------------------------------------------------------


def load_drugs(path: Path) -> list[DrugRow]:
    if not path.exists():
        raise SystemExit(f"[오류] {path} 가 없다.")

    drugs: list[DrugRow] = []
    with path.open(newline="", encoding="utf-8") as f:
        for idx, rec in enumerate(csv.DictReader(f), start=1):
            category = rec["category"].strip()
            package_unit = rec["package_unit"].strip()
            if category not in DRUG_CATEGORIES:
                raise SystemExit(f"[오류] drug_master.csv {idx}행: category '{category}' 는 허용된 7종 밖이다.")
            if not package_unit:
                raise SystemExit(f"[오류] drug_master.csv {idx}행: package_unit 이 비어 있다.")

            item_seq = rec["item_seq"].strip() or f"SEED{idx:05d}"
            base_price_raw = rec["base_price"].strip()
            drugs.append(
                DrugRow(
                    item_seq=item_seq,
                    name=rec["name"].strip(),
                    display_name=rec["display_name"].strip(),
                    maker=rec["maker"].strip() or None,
                    category=category,
                    form=rec["form"].strip() or None,
                    package_unit=package_unit,
                    otc_flag=rec["otc_flag"].strip().lower() == "true",
                    base_price=int(base_price_raw) if base_price_raw else None,
                )
            )

    if len(drugs) < 30:
        raise SystemExit(f"[오류] 의약품이 {len(drugs)}종으로 최소 30종에 못 미친다.")
    return drugs


# -----------------------------------------------------------------------------
# 5. app_user — 관리자 + 더미 제보자
# -----------------------------------------------------------------------------


def hash_password(plain: str) -> str:
    return bcrypt.hashpw(plain.encode("utf-8"), bcrypt.gensalt(rounds=BCRYPT_ROUNDS)).decode("ascii")


def build_users() -> list[UserRow]:
    users = [UserRow(email=ADMIN_EMAIL, password_hash=hash_password(ADMIN_PASSWORD), nickname="관리자", role="ADMIN")]
    dummy_hash = hash_password(DUMMY_PASSWORD)  # 20명이 같은 데모 비밀번호를 쓴다 — 시드용이라 문제 없음
    for i in range(1, DUMMY_USER_COUNT + 1):
        users.append(
            UserRow(email=f"user{i:02d}@example.com", password_hash=dummy_hash, nickname=f"제보자{i:02d}")
        )
    return users


# -----------------------------------------------------------------------------
# 6. 자체 검증 — SQL을 쓰기 전에 완료 판정을 미리 재현한다
# -----------------------------------------------------------------------------


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    r = 6_371_000.0
    d_lat = math.radians(lat2 - lat1)
    d_lng = math.radians(lng2 - lng1)
    a = (
        math.sin(d_lat / 2) ** 2
        + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(d_lng / 2) ** 2
    )
    return r * 2 * math.asin(math.sqrt(a))


def self_check(regions: list[RegionRow], pharmacies: list[PharmacyRow], drugs: list[DrugRow]) -> None:
    errors: list[str] = []

    if len(regions) < 20:
        errors.append(f"region {len(regions)}행 < 20")
    if not (SAMPLE_MIN <= len(pharmacies) <= SAMPLE_MAX):
        errors.append(f"pharmacy {len(pharmacies)}행이 {SAMPLE_MIN}~{SAMPLE_MAX} 범위 밖")
    if len(drugs) < 30:
        errors.append(f"drug {len(drugs)}행 < 30")
    for p in pharmacies:
        if p.lat is None or p.lng is None or not p.region_code:
            errors.append(f"pharmacy {p.hira_code} 에 lat/lng/region_code 누락")
            break

    # 완료 판정: 임의 좌표에서 반경 2km 안에 약국 5개 이상.
    rng = random.Random(RANDOM_SEED)
    probe_points = rng.sample(pharmacies, min(5, len(pharmacies)))
    for probe in probe_points:
        nearby = sum(
            1 for p in pharmacies if haversine_m(probe.lat, probe.lng, p.lat, p.lng) <= 2000
        )
        if nearby < 5:
            errors.append(
                f"좌표 ({probe.lat:.4f},{probe.lng:.4f}) 기준 2km 내 약국 {nearby}개 < 5개 — 지역 범위를 좁혀 밀도를 높일 것"
            )

    if errors:
        print("[자체 검증 실패]")
        for e in errors:
            print(f"  - {e}")
        raise SystemExit(1)

    print(f"[자체 검증 통과] region={len(regions)} pharmacy={len(pharmacies)} drug={len(drugs)}")


# -----------------------------------------------------------------------------
# 7. SQL 출력
# -----------------------------------------------------------------------------


def sql_str(value: Optional[str]) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("'", "''") + "'"


def sql_num(value) -> str:
    return "NULL" if value is None else str(value)


def render_sql(regions: list[RegionRow], users: list[UserRow], pharmacies: list[PharmacyRow], drugs: list[DrugRow]) -> str:
    now = datetime.now(timezone(timedelta(hours=9))).isoformat(timespec="seconds")
    lines: list[str] = [
        "-- =============================================================================",
        "-- V2__seed_master.sql - 공공데이터 기반 마스터 시드",
        "--",
        f"-- 생성 : tools/seed-generator/build_master_seed.py ({now}, KST)",
        "-- 근거 : docs/ROADMAP.md T-07",
        "-- 재실행 시 이 파일을 다시 생성해도 ON CONFLICT ... DO NOTHING 으로 멱등하다.",
        "-- =============================================================================",
        "",
        "-- -----------------------------------------------------------------------------",
        "-- region",
        "-- -----------------------------------------------------------------------------",
    ]

    for r in regions:
        lines.append(
            "INSERT INTO region (code, sido, sigungu, center_lat, center_lng) VALUES "
            f"({sql_str(r.code)}, {sql_str(r.sido)}, {sql_str(r.sigungu)}, {r.center_lat:.6f}, {r.center_lng:.6f}) "
            "ON CONFLICT (code) DO NOTHING;"
        )

    lines += ["", "-- -----------------------------------------------------------------------------",
              "-- app_user (관리자 1 + 더미 제보자 20)", "-- -----------------------------------------------------------------------------"]
    for u in users:
        lines.append(
            "INSERT INTO app_user (email, password_hash, nickname, role) VALUES "
            f"({sql_str(u.email)}, {sql_str(u.password_hash)}, {sql_str(u.nickname)}, {sql_str(u.role)}) "
            "ON CONFLICT (email) DO NOTHING;"
        )

    lines += ["", "-- -----------------------------------------------------------------------------",
              "-- pharmacy", "-- -----------------------------------------------------------------------------"]
    for p in pharmacies:
        lines.append(
            "INSERT INTO pharmacy (hira_code, name, address_road, region_code, lat, lng, phone) VALUES "
            f"({sql_str(p.hira_code)}, {sql_str(p.name)}, {sql_str(p.address_road)}, "
            f"{sql_str(p.region_code)}, {p.lat:.6f}, {p.lng:.6f}, {sql_str(p.phone)}) "
            "ON CONFLICT (hira_code) DO NOTHING;"
        )

    lines += ["", "-- -----------------------------------------------------------------------------",
              "-- drug", "-- -----------------------------------------------------------------------------"]
    for d in drugs:
        lines.append(
            "INSERT INTO drug (item_seq, name, display_name, maker, category, form, package_unit, otc_flag, base_price) VALUES "
            f"({sql_str(d.item_seq)}, {sql_str(d.name)}, {sql_str(d.display_name)}, {sql_str(d.maker)}, "
            f"{sql_str(d.category)}, {sql_str(d.form)}, {sql_str(d.package_unit)}, {str(d.otc_flag).lower()}, "
            f"{sql_num(d.base_price)}) "
            "ON CONFLICT (item_seq) DO NOTHING;"
        )

    lines.append("")
    return "\n".join(lines)


# -----------------------------------------------------------------------------
# main
# -----------------------------------------------------------------------------


def main() -> None:
    # Windows 콘솔(cp949 등)에서 한글 로그가 깨지는 것을 방지한다. 파일 출력은 이미
    # UTF-8로 고정돼 있으므로(render_sql -> write_text) 이 처리와 무관하게 항상 정상이다.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-fetch", action="store_true", help="HIRA API 호출 없이 캐시된 pharmacy_raw.csv 를 재사용한다")
    parser.add_argument("--pharmacy-raw", type=Path, default=DEFAULT_PHARMACY_RAW)
    parser.add_argument("--drug-csv", type=Path, default=DEFAULT_DRUG_CSV)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--sample-size", type=int, default=SAMPLE_TARGET)
    args = parser.parse_args()

    load_dotenv(SCRIPT_DIR / ".env")

    if args.skip_fetch:
        pharmacies_all = load_pharmacy_raw_csv(args.pharmacy_raw)
    else:
        service_key = os.environ.get("HIRA_SERVICE_KEY")
        if not service_key:
            raise SystemExit(
                "[오류] HIRA_SERVICE_KEY 가 설정되지 않았다. tools/seed-generator/.env 를 만들거나 "
                "--skip-fetch 로 캐시된 데이터를 재사용할 것."
            )
        print("HIRA 약국정보서비스 API 호출 중 (서울·경기)...")
        pharmacies_all = fetch_pharmacies_from_api(service_key)
        save_pharmacy_raw_csv(pharmacies_all, args.pharmacy_raw)

    regions = build_regions(pharmacies_all)
    pharmacies = sample_pharmacies(pharmacies_all, target=args.sample_size)
    # 표본 밖 지역이 region 에 남아 있어도 무방하다(위치 폴백 드롭다운용) — 재계산하지 않는다.
    drugs = load_drugs(args.drug_csv)
    users = build_users()

    self_check(regions, pharmacies, drugs)

    sql = render_sql(regions, users, pharmacies, drugs)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(sql, encoding="utf-8")
    print(f"작성 완료: {args.out}")


if __name__ == "__main__":
    main()
