#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build_price_seed.py — price_report / pharmacy_drug_price_stat 목데이터 생성

docs/ROADMAP.md T-08, docs/DATABASE.md §6.2(가격 제보 생성 규칙)·§5.2(대표가격
재계산 SQL) 참고. build_master_seed.py(T-07)와 마찬가지로 런타임 백엔드가
호출하는 것이 아니라 개발자가 로컬에서 1회 실행해 V3__seed_prices.sql을
만들어내는 오프라인 도구다.

V2__seed_master.sql이 이미 적재한 것과 "정확히 같은" 약국·의약품 표본이 있어야
price_report의 FK가 실제로 존재하는 행을 참조한다. 이를 위해 DB에 접속하는 대신
build_master_seed 모듈을 그대로 import해 sample_pharmacies()/load_drugs()를
재사용한다 — 같은 원본 CSV + 같은 RANDOM_SEED이므로 V2와 동일한 400개 약국·36종
의약품 표본이 결정적으로 재현된다.

pharmacy_id/drug_id/user_id는 전부 자연키(hira_code/item_seq/email)로만 참조한다.
BIGSERIAL PK 값은 이 스크립트가 알 수 없고(DB 미접속 원칙 유지) 알 필요도 없다 —
생성된 SQL이 VALUES 파생 테이블을 자연키로 JOIN해 실제 id를 그때그때 해석한다.

⚠️ uq_report_user_pair_day(유니크 인덱스: user_id, pharmacy_id, drug_id,
(created_at을 KST로 바꾼 날짜)) 위반 주의. Flyway는 이 파일 전체를 한 트랜잭션으로
실행하므로 created_at을 명시하지 않으면 모든 행이 사실상 같은 순간의 값을 갖는다.
같은 (약국,약품) 쌍 안에서 같은 더미 사용자를 두 번 배정하면 그 즉시 이 인덱스를
위반해 마이그레이션 전체가 실패한다. assign_users_for_pair()가 쌍 내부
비복원추출로 이를 원천 차단한다 — created_at 값이 무엇이든 무관하게 안전하다.

사용법:
    python build_price_seed.py                          # V2와 동일 표본으로 V3__seed_prices.sql 생성
    python build_price_seed.py --as-of-date 2026-09-16   # 기준일 고정(재현성 테스트용)

주의: build_master_seed를 import하므로 그 모듈이 요구하는 의존성(bcrypt, requests,
python-dotenv — requirements.txt 참고)이 설치돼 있어야 한다. 이 스크립트 자체는
네트워크 호출도 bcrypt 해시 계산도 하지 않는다(약국은 캐시된 data/pharmacy_raw.csv만
읽는다).
"""

from __future__ import annotations

import argparse
import itertools
import math
import random
import sys
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Optional

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))
import build_master_seed as bms  # noqa: E402  (sys.path 조정 이후에 import해야 한다)

# -----------------------------------------------------------------------------
# 상수 — DATABASE.md §6.2 생성 규칙 그대로. 시드 상수는 새로 정의하지 않고
# build_master_seed.RANDOM_SEED(20260915)를 그대로 참조해 단일 진실 소스를 유지한다.
# -----------------------------------------------------------------------------

DEFAULT_OUTPUT = (
    SCRIPT_DIR.parent.parent
    / "miniproject1-backend"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "migration"
    / "V3__seed_prices.sql"
)

COVERAGE_RATE = 0.60              # (약국,약품) 조합 중 데이터를 가질 비율
REPORT_COUNT_MIN, REPORT_COUNT_MAX = 1, 6
OUTLIER_RATE = 0.02               # 전체 제보 중 오타/이상치로 가격을 덮어쓸 비율
OUTLIER_FLAGGED_SHOW_RATE = 0.10  # 이상치 중 관리자 화면(T-32) 데모용으로 flagged=true 표시할 비율
NULL_USER_PROB = 0.5              # 제보자를 NULL(익명/미상)로 둘 확률
AGE_MAX_DAYS = 120                 # purchased_at 생성식 age = int(120 * r**1.6)의 최댓값

# T-10이 런타임에 구현할 로직과 반드시 일치해야 하는 값 (application.yml 확인 완료:
# recommendation.price-window-days=90, price-window-fallback-days=180,
# outlier.iqr-multiplier=1.5, outlier.min-samples=4).
PRICE_WINDOW_DAYS = 90
PRICE_WINDOW_FALLBACK_DAYS = 180
IQR_MULTIPLIER = 1.5
MIN_SAMPLES_FOR_IQR = 4

MIN_TOTAL_REPORTS = 3000
OUTLIER_RATIO_MIN, OUTLIER_RATIO_MAX = 0.01, 0.03
NO_DATA_PAIR_RATIO_MAX = 0.01      # 모든 제보가 flagged=true라 통계 대상에서 빠지는 쌍의 허용 비율
VALUES_CHUNK_SIZE = 1000


# -----------------------------------------------------------------------------
# 데이터 클래스
# -----------------------------------------------------------------------------


@dataclass(frozen=True)
class ReportRow:
    """price_report 한 행. pharmacy/drug/user는 전부 자연키로만 들고 있는다."""

    hira_code: str
    item_seq: str
    user_email: Optional[str]
    price: int
    purchased_at: date
    flagged: bool
    flag_reason: Optional[str]
    is_outlier: bool  # SQL에는 쓰이지 않는다 — self_check의 이상치 비율 검증 전용


# -----------------------------------------------------------------------------
# 1. 마스터 데이터 로드 — build_master_seed 재사용으로 V2와 동일 표본 재현
# -----------------------------------------------------------------------------


def load_pharmacies(path: Path) -> list[bms.PharmacyRow]:
    """V2가 실제로 적재한 것과 정확히 같은 400개 표본을 재현한다."""
    all_pharmacies = bms.load_pharmacy_raw_csv(path)
    return bms.sample_pharmacies(all_pharmacies, target=bms.SAMPLE_TARGET)


def load_drugs(path: Path) -> list[bms.DrugRow]:
    return bms.load_drugs(path)


def dummy_user_emails() -> list[str]:
    # bms.build_users()는 bcrypt.gensalt()(OS 랜덤)로 비밀번호 해시를 만드는데 여기서는
    # 이메일(자연키)만 필요하므로 호출하지 않는다 — 불필요한 해시 연산과 비결정성을 피한다.
    # admin@example.com은 "더미 제보자 중 랜덤"의 대상이 아니므로 포함하지 않는다.
    return [f"user{i:02d}@example.com" for i in range(1, bms.DUMMY_USER_COUNT + 1)]


# -----------------------------------------------------------------------------
# 2. 사용자 배정 — uq_report_user_pair_day 충돌 원천 차단
# -----------------------------------------------------------------------------


def assign_users_for_pair(
    rng: random.Random, n: int, dummy_emails: list[str], null_prob: float
) -> list[Optional[str]]:
    """같은 쌍 안에서 같은 non-null 사용자가 두 번 배정되지 않도록 비복원추출한다.

    Flyway가 이 파일 전체를 한 트랜잭션으로 실행해 모든 행의 created_at이 사실상
    같은 순간이 되므로, uq_report_user_pair_day(user_id, pharmacy_id, drug_id,
    KST 날짜) 위반을 막는 유일한 필수 장치가 이 비복원추출이다. 한 쌍의 n은 최대
    6, 더미 사용자는 20명이라 후보 고갈 걱정은 없다.
    """
    used: set[str] = set()
    assigned: list[Optional[str]] = []
    for _ in range(n):
        if rng.random() < null_prob or len(used) >= len(dummy_emails):
            assigned.append(None)
            continue
        candidates = [e for e in dummy_emails if e not in used]
        chosen = rng.choice(candidates)
        used.add(chosen)
        assigned.append(chosen)
    return assigned


# -----------------------------------------------------------------------------
# 3. 쌍(pair) 단위 제보 생성 — DATABASE.md §6.2
# -----------------------------------------------------------------------------


def clamp_price(price: float) -> int:
    # ck_price_report_price(100~200000) 방어. base_price 범위(1,500~20,000원)에서는
    # 거의 발동하지 않는 방어적 코드다 (V1__init.sql 주석: "애플리케이션 검증만 믿지 않는다").
    return int(max(100, min(200_000, round(price, -1))))


def generate_reports_for_pair(
    rng: random.Random,
    pharmacy: bms.PharmacyRow,
    drug: bms.DrugRow,
    factor: dict[str, float],
    dummy_emails: list[str],
    today: date,
) -> list[ReportRow]:
    if rng.random() > COVERAGE_RATE:
        return []

    n = rng.randint(REPORT_COUNT_MIN, REPORT_COUNT_MAX)
    users = assign_users_for_pair(rng, n, dummy_emails, NULL_USER_PROB)

    rows: list[ReportRow] = []
    for user_email in users:
        base_price = drug.base_price
        price = base_price * factor[pharmacy.hira_code] * rng.gauss(1, 0.05)

        is_outlier = rng.random() < OUTLIER_RATE
        flagged, flag_reason = False, None
        if is_outlier:
            multiplier = rng.choice([0.3, 3.0])
            price = base_price * multiplier
            if rng.random() < OUTLIER_FLAGGED_SHOW_RATE:
                flagged = True
                flag_reason = "OUTLIER_HIGH" if multiplier > 1 else "OUTLIER_LOW"

        age = int(AGE_MAX_DAYS * rng.random() ** 1.6)  # 최근 쪽에 몰리게, 최댓값 120일
        purchased_at = today - timedelta(days=age)

        rows.append(
            ReportRow(
                hira_code=pharmacy.hira_code,
                item_seq=drug.item_seq,
                user_email=user_email,
                price=clamp_price(price),
                purchased_at=purchased_at,
                flagged=flagged,
                flag_reason=flag_reason,
                is_outlier=is_outlier,
            )
        )
    return rows


def generate_all_reports(
    pharmacies: list[bms.PharmacyRow],
    drugs: list[bms.DrugRow],
    dummy_emails: list[str],
    seed: int,
    today: date,
) -> list[ReportRow]:
    """하나의 random.Random 스트림을 순서대로 소비한다.

    이 순서(약국×의약품 product → 커버리지 판정 → n → 사용자 배정 → 가격/이상치 →
    날짜)를 바꾸면 재현성은 유지되지만 결과가 달라진다. today는 랜덤 소비에
    관여하지 않고 age를 날짜로 변환할 때만 쓰이므로, 다른 날짜에 재실행해도
    가격·이상치·사용자 배정 구조 자체는 완전히 동일하고 purchased_at의 절대값만
    그만큼 밀린다.
    """
    rng = random.Random(seed)
    factor = {p.hira_code: rng.uniform(0.85, 1.25) for p in pharmacies}  # 약국 고유 가격대

    reports: list[ReportRow] = []
    for pharmacy, drug in itertools.product(pharmacies, drugs):
        reports.extend(generate_reports_for_pair(rng, pharmacy, drug, factor, dummy_emails, today))
    return reports


def group_by_pair(reports: list[ReportRow]) -> dict[tuple[str, str], list[ReportRow]]:
    groups: dict[tuple[str, str], list[ReportRow]] = {}
    for r in reports:
        groups.setdefault((r.hira_code, r.item_seq), []).append(r)
    return groups


# -----------------------------------------------------------------------------
# 4. 대표가격 시뮬레이션 (self_check 전용) — DATABASE.md §5.2를 Python으로 재현
# -----------------------------------------------------------------------------


def percentile_cont(sorted_values: list[int], p: float) -> float:
    """PostgreSQL percentile_cont(p) WITHIN GROUP (ORDER BY ...)와 동일한 선형보간."""
    n = len(sorted_values)
    if n == 1:
        return float(sorted_values[0])
    rank = p * (n - 1)
    lo, hi = math.floor(rank), math.ceil(rank)
    if lo == hi:
        return float(sorted_values[lo])
    frac = rank - lo
    return sorted_values[lo] + (sorted_values[hi] - sorted_values[lo]) * frac


def simulate_stat_for_pair(rows: list[ReportRow], today: date) -> Optional[dict]:
    """STAT_RECALC_SQL과 반드시 동기화할 것 — 로직이 어긋나면 self_check가 의미 없어진다.

    유효 데이터가 전혀 없으면(해당 쌍의 모든 제보가 flagged=true인 극단적 케이스)
    None을 반환한다. 이는 오류가 아니라 "그래도 0건이면 통계 대상에서 제외"라는
    스펙대로의 정상 케이스이며, SQL에서도 해당 쌍은 INNER JOIN에 걸리지 않아
    행이 아예 생성되지 않는다.
    """

    def valid_within(days: int) -> list[ReportRow]:
        return [r for r in rows if not r.flagged and (today - r.purchased_at).days <= days]

    candidates = valid_within(PRICE_WINDOW_DAYS)
    window_days = PRICE_WINDOW_DAYS
    if not candidates:
        candidates = valid_within(PRICE_WINDOW_FALLBACK_DAYS)
        window_days = PRICE_WINDOW_FALLBACK_DAYS
    if not candidates:
        return None

    candidates.sort(key=lambda r: r.price)
    n = len(candidates)
    if n < MIN_SAMPLES_FOR_IQR:
        trimmed = candidates
    else:
        vals = [r.price for r in candidates]
        q1 = percentile_cont(vals, 0.25)
        q3 = percentile_cont(vals, 0.75)
        iqr = q3 - q1
        lo, hi = q1 - IQR_MULTIPLIER * iqr, q3 + IQR_MULTIPLIER * iqr
        trimmed = [r for r in candidates if lo <= r.price <= hi]

    trimmed_prices = sorted(r.price for r in trimmed)
    return {
        "rep_price": round(percentile_cont(trimmed_prices, 0.5)),
        "min_price": trimmed_prices[0],
        "max_price": trimmed_prices[-1],
        "avg_price": round(sum(trimmed_prices) / len(trimmed_prices)),
        "report_count": len(trimmed_prices),
        "last_reported_at": max(r.purchased_at for r in trimmed),
        "window_days": window_days,
    }


# -----------------------------------------------------------------------------
# 5. 자체 검증 — SQL을 쓰기 전에 ROADMAP 완료 판정을 미리 재현한다
# -----------------------------------------------------------------------------


def self_check(
    pharmacies: list[bms.PharmacyRow],
    drugs: list[bms.DrugRow],
    reports: list[ReportRow],
    today: date,
) -> None:
    errors: list[str] = []
    total = len(reports)

    # (1) 총 건수, 이상치 비율
    if total < MIN_TOTAL_REPORTS:
        errors.append(f"price_report {total}건 < 최소 {MIN_TOTAL_REPORTS}건")
    outlier_ratio = (sum(1 for r in reports if r.is_outlier) / total) if total else 0.0
    if not (OUTLIER_RATIO_MIN <= outlier_ratio <= OUTLIER_RATIO_MAX):
        errors.append(
            f"이상치 비율 {outlier_ratio:.4f}이 허용 범위 [{OUTLIER_RATIO_MIN}, {OUTLIER_RATIO_MAX}] 밖"
        )

    # (2) rep_price 계산 — 유효 데이터가 있는 쌍은 반드시 결과가 나와야 한다.
    pairs = group_by_pair(reports)
    stats: dict[tuple[str, str], dict] = {}
    no_data_pairs = 0
    for key, rows in pairs.items():
        result = simulate_stat_for_pair(rows, today)
        if result is None:
            no_data_pairs += 1  # 해당 쌍의 모든 제보가 flagged=true인 극단적 케이스 — 정상 제외
            continue
        stats[key] = result
    no_data_ratio = (no_data_pairs / len(pairs)) if pairs else 0.0
    if no_data_ratio > NO_DATA_PAIR_RATIO_MAX:
        errors.append(
            f"유효 데이터가 전혀 없어 통계 대상에서 제외되는 쌍이 {no_data_pairs}개"
            f"({no_data_ratio:.2%}, 허용 {NO_DATA_PAIR_RATIO_MAX:.0%}) — "
            "OUTLIER_FLAGGED_SHOW_RATE 등 이상치 설계를 재검토할 것"
        )

    # (3) 재현성은 main()에서 generate_all_reports()를 두 번 호출해 별도로 검증한다.

    # (4) 임의 약품에 대해 반경 2km 내 후보 약국 5개 이상 (bms.haversine_m 재사용)
    pharmacy_by_code = {p.hira_code: p for p in pharmacies}
    item_seqs_with_data = sorted({key[1] for key in pairs})
    probe_rng = random.Random(bms.RANDOM_SEED)
    probe_drugs = probe_rng.sample(item_seqs_with_data, min(5, len(item_seqs_with_data)))
    for item_seq in probe_drugs:
        codes = {key[0] for key in pairs if key[1] == item_seq}
        candidates = [pharmacy_by_code[c] for c in codes]
        best = 0
        for center in candidates:
            nearby = sum(
                1 for p in candidates if bms.haversine_m(center.lat, center.lng, p.lat, p.lng) <= 2000
            )
            best = max(best, nearby)
        if best < 5:
            errors.append(f"약품 {item_seq}: 반경 2km 내 후보 약국 최대 {best}개 < 5개")

    # (5) 같은 약품에 대해 약국별 rep_price가 실제로 다름 (factor 검증)
    for item_seq in item_seqs_with_data:
        rep_prices = {stats[key]["rep_price"] for key in stats if key[1] == item_seq}
        if len(rep_prices) > 1:
            break
    else:
        errors.append("모든 약품에서 약국별 rep_price가 전부 동일하다 — factor(uniform(0.85,1.25))가 반영되지 않았다")

    if errors:
        print("[자체 검증 실패]")
        for e in errors:
            print(f"  - {e}")
        raise SystemExit(1)

    print(
        f"[자체 검증 통과] price_report={total}건(이상치 {outlier_ratio:.2%}), "
        f"stat 대상 쌍={len(stats)}개(제외 {no_data_pairs}개)"
    )


# -----------------------------------------------------------------------------
# 6. SQL 출력
# -----------------------------------------------------------------------------

STAT_RECALC_SQL = """\
-- =============================================================================
-- pharmacy_drug_price_stat 초기 계산
-- DATABASE.md §5.2 대표가격 재계산(IQR 이상치 제거 + 중앙값)과 완전히 동일한
-- 로직이다. T-10이 런타임에 구현할 native query와 이 CTE 구조가 달라지면 반드시
-- 함께 갱신할 것. 아래 상수(90, 180, 1.5, 4)는 application.yml 의
-- recommendation.price-window-days / price-window-fallback-days /
-- outlier.iqr-multiplier / outlier.min-samples 값과 반드시 일치해야 한다.
-- =============================================================================
WITH pairs AS (
    SELECT DISTINCT pharmacy_id, drug_id FROM price_report
),
pairs_with_90 AS (
    SELECT DISTINCT pharmacy_id, drug_id FROM price_report
    WHERE status = 'ACTIVE' AND flagged = false AND purchased_at >= CURRENT_DATE - 90
),
chosen_window AS (
    SELECT pa.pharmacy_id, pa.drug_id,
           CASE WHEN p90.pharmacy_id IS NOT NULL THEN 90 ELSE 180 END AS window_days
    FROM pairs pa
    LEFT JOIN pairs_with_90 p90
      ON p90.pharmacy_id = pa.pharmacy_id AND p90.drug_id = pa.drug_id
),
valid AS (
    SELECT cw.pharmacy_id, cw.drug_id, cw.window_days, pr.price, pr.purchased_at
    FROM chosen_window cw
    JOIN price_report pr
      ON pr.pharmacy_id = cw.pharmacy_id AND pr.drug_id = cw.drug_id
    WHERE pr.status = 'ACTIVE' AND pr.flagged = false
      AND pr.purchased_at >= CURRENT_DATE - cw.window_days
),
q AS (
    SELECT pharmacy_id, drug_id, window_days,
           percentile_cont(0.25) WITHIN GROUP (ORDER BY price) AS q1,
           percentile_cont(0.75) WITHIN GROUP (ORDER BY price) AS q3,
           count(*) AS n
    FROM valid
    GROUP BY pharmacy_id, drug_id, window_days
),
trimmed AS (
    SELECT v.pharmacy_id, v.drug_id, v.window_days, v.price, v.purchased_at
    FROM valid v
    JOIN q ON q.pharmacy_id = v.pharmacy_id AND q.drug_id = v.drug_id
    WHERE q.n < 4
       OR v.price BETWEEN q.q1 - 1.5 * (q.q3 - q.q1)
                      AND q.q3 + 1.5 * (q.q3 - q.q1)
)
INSERT INTO pharmacy_drug_price_stat
    (pharmacy_id, drug_id, rep_price, min_price, max_price, avg_price,
     report_count, last_reported_at, window_days)
SELECT
    pharmacy_id, drug_id,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY price)::int AS rep_price,
    MIN(price)::int  AS min_price,
    MAX(price)::int  AS max_price,
    AVG(price)::int  AS avg_price,
    COUNT(*)::int    AS report_count,
    MAX(purchased_at) AS last_reported_at,
    window_days
FROM trimmed
GROUP BY pharmacy_id, drug_id, window_days
ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;
"""


def render_price_report_values_row(r: ReportRow) -> str:
    user_email_sql = "NULL::varchar(255)" if r.user_email is None else bms.sql_str(r.user_email)
    flag_reason_sql = "NULL::varchar(20)" if r.flag_reason is None else bms.sql_str(r.flag_reason)
    return (
        f"({bms.sql_str(r.hira_code)}, {bms.sql_str(r.item_seq)}, {user_email_sql}, "
        f"{r.price}, '{r.purchased_at.isoformat()}'::date, {str(r.flagged).lower()}, {flag_reason_sql})"
    )


def render_price_report_sql(reports: list[ReportRow], chunk_size: int = VALUES_CHUNK_SIZE) -> str:
    """행 단위 INSERT...SELECT 대신 VALUES 파생 테이블을 청크 단위로 자연키 JOIN한다.

    hira_code/item_seq/email 모두 UNIQUE 제약(자동 btree 인덱스)이 있어 각 JOIN이
    인덱스 스캔으로 처리된다 — 수만 행이라도 청크당 1,000행이면 충분히 빠르다.
    """
    lines = [
        "-- -----------------------------------------------------------------------------",
        f"-- price_report ({len(reports)}건, VALUES {chunk_size}행 단위 청크)",
        "-- 자연키(hira_code/item_seq/email)로 JOIN해 실제 pharmacy_id/drug_id/user_id를 해석한다.",
        "-- -----------------------------------------------------------------------------",
        "",
    ]
    for start in range(0, len(reports), chunk_size):
        chunk = reports[start : start + chunk_size]
        values_sql = ",\n    ".join(render_price_report_values_row(r) for r in chunk)
        lines.append(
            "INSERT INTO price_report "
            "(pharmacy_id, drug_id, user_id, price, purchased_at, source, status, flagged, flag_reason)\n"
            "SELECT ph.id, dr.id, au.id, v.price, v.purchased_at, 'SEED', 'ACTIVE', v.flagged, v.flag_reason\n"
            "FROM (VALUES\n    " + values_sql + "\n) AS v(hira_code, item_seq, user_email, price, "
            "purchased_at, flagged, flag_reason)\n"
            "JOIN pharmacy ph ON ph.hira_code = v.hira_code\n"
            "JOIN drug     dr ON dr.item_seq  = v.item_seq\n"
            "LEFT JOIN app_user au ON au.email = v.user_email;"
        )
        lines.append("")
    return "\n".join(lines)


def render_sql(reports: list[ReportRow]) -> str:
    now = datetime.now(timezone(timedelta(hours=9))).isoformat(timespec="seconds")
    header = [
        "-- =============================================================================",
        "-- V3__seed_prices.sql - 가격 제보 목데이터",
        "--",
        f"-- 생성 : tools/seed-generator/build_price_seed.py ({now}, KST)",
        "-- 근거 : docs/ROADMAP.md T-08, docs/DATABASE.md §6.2(생성 규칙)/§5.2(대표가격 재계산)",
        "-- V2__seed_master.sql이 만든 pharmacy/drug/app_user를 자연키로 참조한다.",
        "-- =============================================================================",
        "",
    ]
    analyze_note = (
        "\n-- price_report를 방금 대량 INSERT한 직후라 플래너 통계가 비어 있다. ANALYZE 없이\n"
        "-- 바로 STAT_RECALC_SQL(그룹별 percentile_cont 집계)을 돌리면 카디널리티 추정이\n"
        "-- 완전히 틀어져 최악의 실행계획(예: nested loop)을 골라 표본이 클 때 극단적으로\n"
        "-- 느려진다(실측: 약국 2,485건·제보 18.8만 건 규모에서 ANALYZE 없이 13분 넘게\n"
        "-- 끝나지 않아 취소함). Flyway 트랜잭션 안에서도 ANALYZE는 그 시점까지의 변경\n"
        "-- 내용을 볼 수 있다.\n"
        "ANALYZE price_report;\n"
    )
    return "\n".join(header) + render_price_report_sql(reports) + analyze_note + "\n" + STAT_RECALC_SQL


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
    parser.add_argument("--pharmacy-raw", type=Path, default=bms.DEFAULT_PHARMACY_RAW)
    parser.add_argument("--drug-csv", type=Path, default=bms.DEFAULT_DRUG_CSV)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument(
        "--as-of-date",
        type=str,
        default=None,
        help="기준일(YYYY-MM-DD). 생략 시 오늘. purchased_at은 이 날짜 기준 최근 120일 이내로 분포한다",
    )
    args = parser.parse_args()

    today = date.fromisoformat(args.as_of_date) if args.as_of_date else date.today()

    pharmacies = load_pharmacies(args.pharmacy_raw)
    drugs = load_drugs(args.drug_csv)
    dummy_emails = dummy_user_emails()
    print(f"약국 {len(pharmacies)}건, 의약품 {len(drugs)}종, 더미 제보자 {len(dummy_emails)}명 로드 완료")

    reports = generate_all_reports(pharmacies, drugs, dummy_emails, bms.RANDOM_SEED, today)

    # 재현성 확인 — 같은 인자로 한 번 더 생성해 완전히 같은 결과가 나오는지 검증한다.
    reports_repeat = generate_all_reports(pharmacies, drugs, dummy_emails, bms.RANDOM_SEED, today)
    if reports != reports_repeat:
        raise SystemExit("[오류] 같은 시드로 두 번 생성한 결과가 다르다 — 재현성이 깨졌다")

    self_check(pharmacies, drugs, reports, today)

    sql = render_sql(reports)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(sql, encoding="utf-8")
    print(f"작성 완료: {args.out} ({len(reports)}건)")


if __name__ == "__main__":
    main()
