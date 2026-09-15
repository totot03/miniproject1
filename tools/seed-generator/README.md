# seed-generator

공공데이터를 내려받아 `region` / `pharmacy` / `drug` 시드 SQL로 변환하는 Python 스크립트가 위치한다.
[docs/ROADMAP.md](../../docs/ROADMAP.md)의 T-07(마스터 시드)·T-08(가격 제보 시드)에서 채워진다.

## 예정 구조

- `build_master_seed.py` — 약국·의약품·행정구역 → `V2__seed_master.sql`
- `build_price_seed.py` — 가격 제보 목데이터 → `V3__seed_prices.sql`
- `data/` — 원본 CSV (재현성 확보를 위해 커밋, 용량이 크면 필터링 후 저장)
