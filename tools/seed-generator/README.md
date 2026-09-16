# seed-generator

공공데이터를 내려받아 `region` / `pharmacy` / `drug` 시드 SQL로 변환하는 Python 스크립트가 위치한다.
[docs/ROADMAP.md](../../docs/ROADMAP.md)의 T-07(마스터 시드)·T-08(가격 제보 시드)에서 채워진다.

## 구조

- `build_master_seed.py` — 약국·의약품·행정구역 → `V2__seed_master.sql` (T-07)
- `build_price_seed.py` — 가격 제보 목데이터 → `V3__seed_prices.sql` (T-08, 아직 없음)
- `data/pharmacy_raw.csv` — HIRA API에서 받아 `약국`으로 필터링한 원본 데이터 (재현성 확보용 커밋)
- `data/drug_master.csv` — 일반의약품 30종 이상 수작업 목록. `base_price`는 시중가 참고 수기 입력, 운영 시 미사용

## 데이터 소스

| 대상 | 소스 | 인증 |
|---|---|---|
| 약국(`pharmacy`) | 심평원 **약국정보서비스** OpenAPI `getParmacyBasisList` ([data.go.kr id 15001673](https://www.data.go.kr/data/15001673/openapi.do)) | 서비스키 필요 (회원가입 후 활용신청, 자동승인) |
| 의약품(`drug`) | `data/drug_master.csv` 수작업 목록 | 불필요 |
| 행정구역(`region`) | 약국 API 응답의 시도/시군구 코드로 자동 구성 (별도 데이터셋 없음) | 불필요 |

약국을 별도 파일데이터(XLSX) 대신 OpenAPI로 받는 이유, 의약품을 e약은요 API 대신 수작업 목록으로 채운 이유는 `.claude/plans/plan-task-t-07-fizzy-quasar.md`에 남겨 두었다.

## 실행 방법

```bash
cd tools/seed-generator
python -m venv .venv
.venv\Scripts\activate        # PowerShell: .venv\Scripts\Activate.ps1
pip install -r requirements.txt

cp .env.example .env           # HIRA_SERVICE_KEY 값을 채운다
python build_master_seed.py    # V2__seed_master.sql 생성
```

- 서비스키 없이 다시 만들고 싶으면(이미 `data/pharmacy_raw.csv`가 있는 경우) `python build_master_seed.py --skip-fetch`.
- 랜덤 표본은 `random.seed(20260915)`로 고정돼 있어(T-08과 동일 컨벤션) **같은 원본 데이터로 재실행하면 항상 같은 결과**가 나온다.
- 실행 마지막에 완료 판정(약국 300~500건, 의약품 30종 이상, 지역 20건 이상, 임의 좌표 2km 반경 내 약국 5개 이상)을 스스로 검증하고, 하나라도 실패하면 SQL을 쓰지 않고 종료한다.
- 출력 파일은 `miniproject1-backend/src/main/resources/db/migration/V2__seed_master.sql`. 모든 INSERT가 `ON CONFLICT ... DO NOTHING`이라 마이그레이션을 여러 번 적용해도 중복이 생기지 않는다.
