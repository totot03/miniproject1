# miniProject1 — pharmaprice

동네 약국의 일반의약품 가격을 검색·비교하는 미니 프로젝트다. 상세 요구사항은 [docs/PRD.md](./docs/PRD.md), DB 설계는 [docs/DATABASE.md](./docs/DATABASE.md), 작업 계획은 [docs/ROADMAP.md](./docs/ROADMAP.md)를 참고한다.

> 이 문서는 아직 "로컬 환경 준비" 절만 채워진 상태다. 프로젝트 개요·실행 방법 등 나머지 절은 `docs/ROADMAP.md`의 T-37(마감) 단계에서 보완할 예정이다.

---

## 로컬 환경 준비

### 1. PostgreSQL 17 설치 확인

Docker를 쓰지 않고 **로컬에 설치된 PostgreSQL 17**을 그대로 사용한다.

- **Windows**: [postgresql.org/download/windows](https://www.postgresql.org/download/windows/) 설치 프로그램. 설치 중 Locale은 `C` 또는 `en_US.UTF-8`로 둔다. 설치 후 Windows 서비스(`postgresql-x64-17`)가 자동 등록되어 실행된다.
- **macOS**: `brew install postgresql@17` → `brew services start postgresql@17`
  PATH 추가: `echo 'export PATH="/opt/homebrew/opt/postgresql@17/bin:$PATH"' >> ~/.zshrc`
- **Linux**: 배포판 패키지 또는 PGDG 저장소.

Windows에서 `psql`이 PATH에 없다면 전체 경로(`C:\Program Files\PostgreSQL\17\bin\psql.exe`)로 실행하거나, 해당 경로를 PATH에 추가한다.

### 2. 계정 · 데이터베이스 · 확장 · 타임존 생성

`postgres` 슈퍼유저로 접속해 아래 SQL을 실행한다. `.env.example`의 값과 반드시 일치시킨다.

```sql
-- 계정 생성
CREATE USER pharmaprice WITH PASSWORD 'changeme';

-- 본 데이터베이스
CREATE DATABASE pharmaprice OWNER pharmaprice ENCODING 'UTF8';

\c pharmaprice
GRANT ALL ON SCHEMA public TO pharmaprice;

-- 타임존을 Asia/Seoul로 고정한다.
-- UTC로 두면 한국 시간 오전 9시 이전에 CURRENT_DATE가 전날로 잡혀
-- 중복 제보 방지·날짜 검증이 하루씩 어긋난다 (docs/DATABASE.md §8 참고).
ALTER DATABASE pharmaprice SET timezone TO 'Asia/Seoul';

-- pg_trgm 확장 (약국명·약품명 부분검색용).
-- 일반 유저는 권한이 없어 실패할 수 있으므로 슈퍼유저로 미리 설치해 둔다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 테스트용 데이터베이스 (Docker 미사용 환경에서 통합 테스트용)
\c postgres
CREATE DATABASE pharmaprice_test OWNER pharmaprice ENCODING 'UTF8';

\c pharmaprice_test
GRANT ALL ON SCHEMA public TO pharmaprice;
ALTER DATABASE pharmaprice_test SET timezone TO 'Asia/Seoul';
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

### 3. 접속 확인

```bash
psql -h localhost -p 5432 -U pharmaprice -d pharmaprice -c "SELECT version();"
```

접속 후 아래를 확인한다.

- `SHOW timezone;` → `Asia/Seoul`
- `SELECT now();` → `+09` 오프셋
- `SELECT * FROM pg_extension WHERE extname = 'pg_trgm';` → 1행 반환

`pharmaprice_test`에도 동일하게 접속해 같은 결과가 나오는지 확인한다.

> 포트 5432가 이미 다른 PostgreSQL 설치본에 쓰이고 있으면 충돌한다. 필요하면 5433 등으로 바꾸되, `.env`에도 동일하게 반영한다.

### 4. 환경변수 파일 준비

```bash
cp .env.example .env
```

`.env`는 `.gitignore`에 의해 추적되지 않는다(이후 `.gitignore` 추가 시 반영). 비밀번호(`POSTGRES_PASSWORD`)는 각자 로컬 환경에서 자유롭게 바꿔도 된다 — 단, 1번 단계의 `CREATE USER` 비밀번호와 `.env`의 값을 반드시 일치시킨다.
