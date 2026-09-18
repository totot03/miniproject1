# miniProject1 — pharmaprice

동네 약국의 일반의약품 가격을 검색·비교하는 미니 프로젝트다. 검색창에 약 이름을 입력하면 반경 내 약국의 최저가를 가격·거리·정보 신선도 기준으로 추천하고, 회원이 가격을 제보하면 순위에 곧바로 반영된다. 상세 요구사항은 [docs/PRD.md](./docs/PRD.md), DB 설계는 [docs/DATABASE.md](./docs/DATABASE.md), 작업 계획은 [docs/ROADMAP.md](./docs/ROADMAP.md)를 참고한다.

> **목데이터 고지**: 약국 위치는 공공데이터를 사용했지만 가격·제보·계정은 전부 학습 목적으로 생성한 가짜 데이터다. 실제 판매가가 아니며, 이 정보를 근거로 약국을 방문해서는 안 된다. 대외 공개·배포하지 않는 비공개 데모다.

---

## 요구 사항

- JDK 21
- Node 22
- PostgreSQL 17 (Docker 미사용, 로컬 설치)

---

## 개발 컨벤션

### 브랜치

`feature/기능명`, `fix/버그명`, `dev/개발명`, `hotfix/수정명`

### 커밋 메시지

`<type>(<태스크번호>): <설명>` 형식을 쓴다. 태스크 번호는 [docs/ROADMAP.md](./docs/ROADMAP.md)의 `T-xx`를 그대로 쓰고, 작은 단위로 나눠 커밋한다.

예: `feat(T-09): 거리 계산 컴포넌트 추가`

`type`: `feat` / `fix` / `docs` / `chore` / `test` / `refactor`

### 코드 스타일

`.editorconfig`로 강제한다 — Java 4칸 들여쓰기, TS/JS 2칸, LF, UTF-8, 파일 끝 개행.

### 줄바꿈

`.gitattributes`가 저장소 차원에서 LF로 고정한다 (Windows 배치 파일 `*.bat`/`*.cmd`만 CRLF).

Windows의 git은 보통 `core.autocrlf = true`로 설치되어 체크아웃 시 텍스트 파일을 CRLF로 바꾼다. 그러면 에디터는 `.editorconfig`에 따라 LF로 저장하고 git은 CRLF를 기대해, 줄바꿈만 바뀐 diff가 끝없이 생긴다.

`.gitattributes`의 `eol` 지정은 `core.autocrlf`보다 우선하므로 **각자 로컬에서 `core.autocrlf`를 손댈 필요가 없다.** 파일 하나로 팀원 모두의 동작이 같아진다.

이미 잘못된 줄바꿈으로 체크아웃된 상태라면 한 번 재정규화한다.

```bash
git add --renormalize .
git status          # 변경된 파일이 없으면 이미 정상이다
```

### 문서 구조에 대한 예외

[docs/ROADMAP.md](./docs/ROADMAP.md)의 T-01 원안은 `PRD.md`·`DATABASE.md`·`API.md`·`ROADMAP.md` 4종을 저장소 루트에 평평하게 두는 것이었다. 이 프로젝트는 코드 모듈(`miniproject1-frontend/`, `miniproject1-backend/`, `tools/`)과 문서를 시각적으로 분리하기 위해 `docs/` 폴더 아래 유지하기로 했다. 이후 문서나 태스크 카드에서 `PRD.md`처럼 루트 상대경로로 언급되는 부분은 전부 `docs/` 하위로 읽는다.

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

---

## 실행 명령

`.env` 준비가 끝났으면 터미널 2개를 열어 백엔드와 프론트를 각각 기동한다.

**터미널 1 — 백엔드**

```bash
cd miniproject1-backend
./mvnw spring-boot:run
```

로그에 `Started MiniProject1BackendApplication`이 뜨면 정상 기동이다(`http://localhost:8080`). 최초 기동 시 Flyway가 스키마·시드(약국 400건 등)를 자동으로 채운다.

**터미널 2 — 프론트**

```bash
cd miniproject1-frontend
npm install
npm run dev
```

`http://localhost:3000`에서 접속한다. 프론트 환경변수는 루트 `.env`가 아니라 `miniproject1-frontend/.env.local`에 별도로 둔다(아래 환경변수 절 참고).

---

## 환경변수

| 변수 | 위치 | 설명 | 필수 |
| --- | --- | --- | --- |
| `POSTGRES_HOST` | 루트 `.env` | PostgreSQL 호스트 | 예 (기본 `localhost`) |
| `POSTGRES_PORT` | 루트 `.env` | PostgreSQL 포트 | 예 (기본 `5432`) |
| `POSTGRES_DB` | 루트 `.env` | 데이터베이스 이름 | 예 (기본 `pharmaprice`) |
| `POSTGRES_USER` | 루트 `.env` | DB 계정 | 예 (기본 `pharmaprice`) |
| `POSTGRES_PASSWORD` | 루트 `.env` | DB 비밀번호. 1번 단계의 `CREATE USER` 비밀번호와 일치해야 함 | 예 |
| `JWT_SECRET` | 루트 `.env` | JWT 서명 키. 최소 32바이트(256비트) 랜덤 문자열 — 짧으면 `WeakKeyException`으로 기동 자체가 실패한다 | 예 |
| `TZ` | 루트 `.env` | 서버 타임존 | 예 (`Asia/Seoul` 고정) |
| `NEXT_PUBLIC_API_BASE_URL` | `miniproject1-frontend/.env.local` | 프론트가 호출할 백엔드 주소 | 예 (기본 `http://localhost:8080`) |
| `NEXT_PUBLIC_KAKAO_MAP_KEY` | `miniproject1-frontend/.env.local` | 카카오맵 JavaScript 키(카카오 개발자 콘솔 발급, `localhost` 도메인 등록 필요) | 예 (지도 기능에 한해) |

> 루트 `.env`와 `miniproject1-frontend/.env.local`은 서로 다른 파일이다. 둘 다 채워야 백엔드·프론트가 모두 정상 동작한다.

---

## 기본 계정

시드 데이터에 관리자 계정이 포함되어 있다.

| 이메일 | 비밀번호 | 권한 |
| --- | --- | --- |
| `admin@example.com` | `Admin1234!` | ADMIN |

일반 사용자는 `/signup`에서 직접 가입한다.

---

## 문서 링크

- [docs/PRD.md](./docs/PRD.md) — 요구사항 정의
- [docs/DATABASE.md](./docs/DATABASE.md) — DB 스키마 설계
- [docs/API.md](./docs/API.md) — API 명세
- [docs/ROADMAP.md](./docs/ROADMAP.md) — 태스크 분해·진행 방식

---

## 트러블슈팅

**포트 5432·8080·3000 충돌**
다른 프로세스가 이미 해당 포트를 쓰고 있으면 기동이 실패한다. `netstat -ano | findstr :5432`(Windows) 등으로 점유 프로세스를 확인해 종료하거나, PostgreSQL 포트를 바꿨다면 `.env`의 `POSTGRES_PORT`도 함께 바꾼다.

**`pg_trgm` 권한 오류**
일반 유저 계정은 확장 설치 권한이 없다. `postgres` 슈퍼유저로 접속해 `CREATE EXTENSION IF NOT EXISTS pg_trgm;`을 먼저 실행한다.

**카카오맵이 로딩되지 않음(키 도메인 미등록)**
카카오 개발자 콘솔의 JavaScript 키에 사용 중인 도메인이 등록되어 있지 않으면 지도가 조용히 빈 화면으로 남는다. 콘솔에서 플랫폼 도메인을 등록한다.

**Flyway 체크섬 불일치**
이미 적용한 마이그레이션 파일(`V1__init.sql` 등)을 수정하면 발생한다. 개발 중이라면 DB를 재생성해 처음부터 다시 적용하거나(로컬 환경 준비 2번 SQL 재실행), `flyway repair`로 체크섬을 갱신한다.

**JDK 버전 불일치**
`java.version`이 21로 고정되어 있다(`miniproject1-backend/pom.xml`). `java -version`으로 실행 중인 JDK가 21인지 확인한다.

**`ddl-auto: validate` 실패**
Hibernate가 엔티티와 실제 테이블 스키마가 다르다고 판단하면 기동을 거부한다. 마이그레이션 파일과 엔티티 정의가 어긋난 것이므로, DB를 재생성해 마이그레이션을 처음부터 다시 적용해본다.

**`JWT_SECRET`을 안 채웠는데 원인 불명의 에러로 기동이 실패함 (실측)**
`app.jwt.secret`은 기본값이 없어 환경변수가 비어 있으면 Spring이 플레이스홀더 문자열(`${JWT_SECRET}`, 13자)을 그대로 시크릿으로 써버려 `WeakKeyException`(키가 256비트 미만)으로 기동에 실패한다. 게다가 `spring-boot-devtools`가 붙어 있어 Maven은 이 실패를 `BUILD SUCCESS`로 표시하고 조용히 재시작 대기 상태에 빠진다 — 로그에서 `Started MiniProject1BackendApplication`이 실제로 찍혔는지 반드시 확인한다. `.env`에 32바이트 이상의 랜덤 문자열을 채우면 해결되며, 자동 로딩은 `DotenvEnvironmentPostProcessor`가 처리한다.

**프론트에서 지도·API 호출이 전부 실패함 (실측)**
`NEXT_PUBLIC_*` 값은 루트 `.env`가 아니라 `miniproject1-frontend/.env.local`에 있어야 Next.js가 읽는다. 루트 `.env`만 채우고 프론트 폴더의 `.env.local`을 빠뜨리면 카카오맵 키와 API 주소가 모두 비어 화면이 조용히 깨진다.
