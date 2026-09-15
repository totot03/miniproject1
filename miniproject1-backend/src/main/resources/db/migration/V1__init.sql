-- =============================================================================
-- V1__init.sql - 초기 스키마
--
-- 근거 문서 : docs/DATABASE.md v1.3 §3 (테이블 정의) · docs/ROADMAP.md T-05
-- 대상 DBMS : PostgreSQL 17
-- 구성 순서 : 확장 -> 테이블(FK 의존 순) -> 인덱스 -> 테이블 주석
--
-- 설계 원칙 (DATABASE.md §1)
--   1. PK 는 BIGSERIAL. 단 region 만 행정표준코드를 자연키로 쓴다.
--   2. 삭제는 소프트 삭제. 제보는 물리 삭제하지 않고 status 로 관리한다.
--   3. 집계는 pharmacy_drug_price_stat 캐시 테이블에 둔다.
--   4. PostGIS 없이 lat/lng DOUBLE PRECISION + 바운딩 박스 선필터 + Haversine.
--   5. 시각은 전부 TIMESTAMPTZ. 서버·DB 타임존은 Asia/Seoul 로 고정한다.
--   6. 금액은 INTEGER (원 단위). KRW 는 소수점이 없으므로 NUMERIC 불필요.
--
-- 주의 : 이 파일은 Flyway 가 체크섬을 기록한 뒤에는 수정하면 안 된다.
--        스키마 변경은 항상 새 V2/V3... 마이그레이션으로 추가한다.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 0. 확장
-- -----------------------------------------------------------------------------

-- 약국명·약품명 부분검색(trigram)용.
-- README "로컬 환경 준비" 절에서 슈퍼유저로 미리 설치하므로 보통 NOTICE 만 내고
-- 통과한다. 권한 있는 롤이 빈 DB 에 적용하는 경우를 위해 남겨 둔다.
CREATE EXTENSION IF NOT EXISTS pg_trgm;


-- -----------------------------------------------------------------------------
-- 1. region - 행정구역
--    시·군·구 단위. 위치 권한이 거부됐을 때 폴백 좌표로 사용한다.
-- -----------------------------------------------------------------------------

CREATE TABLE region (
    code       VARCHAR(10)      PRIMARY KEY,  -- 행정표준코드 (예: 11680 강남구)
    sido       VARCHAR(20)      NOT NULL,     -- 시도명 (예: 서울특별시)
    sigungu    VARCHAR(30)      NOT NULL,     -- 시군구명 (예: 강남구)
    center_lat DOUBLE PRECISION NOT NULL,     -- 구역 중심 위도
    center_lng DOUBLE PRECISION NOT NULL,     -- 구역 중심 경도

    CONSTRAINT ck_region_center_lat CHECK (center_lat BETWEEN -90 AND 90),
    CONSTRAINT ck_region_center_lng CHECK (center_lng BETWEEN -180 AND 180)
);


-- -----------------------------------------------------------------------------
-- 2. app_user - 사용자
--    user 는 PostgreSQL 예약어라 app_user 를 쓴다. 엔티티명도 AppUser.
-- -----------------------------------------------------------------------------

CREATE TABLE app_user (
    id            BIGSERIAL    PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,            -- 로그인 ID (UNIQUE 제약이 인덱스를 자동 생성)
    password_hash VARCHAR(100) NOT NULL,                   -- BCrypt (strength 10)
    nickname      VARCHAR(30)  NOT NULL,                   -- 제보자 표시명
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',    -- USER | ADMIN
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | SUSPENDED
    report_count  INTEGER      NOT NULL DEFAULT 0,         -- 누적 제보 수(비정규화). 제보 생성 시 +1, REJECTED 처리 시 -1
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),     -- 갱신은 JPA Auditing 이 담당한다 (DB 트리거 없음)

    CONSTRAINT ck_app_user_role         CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_app_user_status       CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_app_user_report_count CHECK (report_count >= 0)
);


-- -----------------------------------------------------------------------------
-- 3. refresh_token - 리프레시 토큰
--    로그아웃 시 서버에서 토큰을 무효화하기 위해 발급분을 보관한다.
--    /auth/refresh 는 기존 행을 revoke 하고 새 행을 발급한다 (rotation).
-- -----------------------------------------------------------------------------

CREATE TABLE refresh_token (
    id         BIGSERIAL   PRIMARY KEY,
    -- 토큰은 사용자에 완전히 종속된 파생 데이터이므로 CASCADE.
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,  -- 토큰 원문의 SHA-256. 원문은 저장하지 않는다
    expires_at TIMESTAMPTZ NOT NULL,         -- 발급 + 14일
    revoked_at TIMESTAMPTZ,                  -- 로그아웃 시각. NULL 이면 유효
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- -----------------------------------------------------------------------------
-- 4. pharmacy - 약국
-- -----------------------------------------------------------------------------

CREATE TABLE pharmacy (
    id             BIGSERIAL        PRIMARY KEY,
    hira_code      VARCHAR(30)      UNIQUE,  -- 심평원 요양기관기호. 공공데이터 재적재 시 멱등성 키(V2 의 ON CONFLICT 대상)
    name           VARCHAR(100)     NOT NULL,
    address_road   VARCHAR(255),             -- 도로명 주소
    address_jibun  VARCHAR(255),             -- 지번 주소
    -- 제보가 걸린 지역을 실수로 지우지 못하도록 기본 동작(NO ACTION)을 유지한다.
    region_code    VARCHAR(10)      REFERENCES region (code),
    lat            DOUBLE PRECISION NOT NULL,  -- 위도 (WGS84)
    lng            DOUBLE PRECISION NOT NULL,  -- 경도 (WGS84)
    phone          VARCHAR(20),
    business_hours JSONB,                      -- {"mon":["09:00","19:00"], ..., "holiday":null}
    is_active      BOOLEAN          NOT NULL DEFAULT true,  -- 폐업 시 false
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),

    -- T-07 공공데이터 적재에서 lat/lng 가 뒤바뀌는 사고를 DB 가 잡아 준다.
    -- 국내 좌표는 lat 약 33~39, lng 약 124~132 라서 뒤바뀌면 lat 이 90 을 넘는다.
    CONSTRAINT ck_pharmacy_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_pharmacy_lng CHECK (lng BETWEEN -180 AND 180)
);


-- -----------------------------------------------------------------------------
-- 5. drug - 일반의약품 마스터
-- -----------------------------------------------------------------------------

CREATE TABLE drug (
    id           BIGSERIAL    PRIMARY KEY,
    item_seq     VARCHAR(20)  UNIQUE,     -- 식약처 품목기준코드
    name         VARCHAR(200) NOT NULL,   -- 품목명 (예: 타이레놀정500밀리그람)
    display_name VARCHAR(100) NOT NULL,   -- 검색·표시용 짧은 이름 (예: 타이레놀 500mg)
    maker        VARCHAR(100),            -- 제조/수입사
    -- 해열진통 | 소화제 | 감기약 | 연고 | 소독약 | 비타민 | 기타
    -- CHECK 를 걸지 않는 이유: T-07 이 식약처 데이터에서 분류를 유도하므로 값 집합이
    -- 아직 유동적이다. DB 제약으로 묶으면 분류 하나 추가할 때마다 마이그레이션이 필요해진다.
    category     VARCHAR(50)  NOT NULL,
    form         VARCHAR(50),             -- 제형 (정제, 캡슐, 시럽, 연고 등)
    -- 판매 단위 (예: 8정, 10ml). 포장 단위가 다르면 별개의 drug 행으로 취급하므로
    -- 가격 비교의 전제가 된다. 그래서 NOT NULL.
    package_unit VARCHAR(50)  NOT NULL,
    otc_flag     BOOLEAN      NOT NULL DEFAULT true,  -- 일반의약품 여부. false 는 적재하지 않음
    base_price   INTEGER,                             -- 시드 생성용 기준가. 운영 시 미사용
    image_url    VARCHAR(500),                        -- 낱알 이미지
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_drug_base_price CHECK (base_price IS NULL OR base_price > 0)
);


-- -----------------------------------------------------------------------------
-- 6. uploaded_file - 업로드 파일
--    price_report.receipt_file_id 가 참조하므로 price_report 보다 먼저 생성한다.
-- -----------------------------------------------------------------------------

CREATE TABLE uploaded_file (
    id            BIGSERIAL    PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_path   VARCHAR(500) NOT NULL,  -- 예: /app/uploads/2026/09/{uuid}.jpg
    content_type  VARCHAR(100) NOT NULL,  -- image/jpeg 등
    size_bytes    BIGINT       NOT NULL,  -- 5MB 이하 (spring.servlet.multipart.max-file-size)
    uploaded_by   BIGINT       REFERENCES app_user (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_uploaded_file_size CHECK (size_bytes > 0)
);


-- -----------------------------------------------------------------------------
-- 7. price_report - 가격 제보 (핵심 테이블)
-- -----------------------------------------------------------------------------

CREATE TABLE price_report (
    id              BIGSERIAL   PRIMARY KEY,
    -- 마스터 참조는 전부 기본 동작(NO ACTION).
    -- 제보가 남아 있는 약국·약품·사용자를 지우려면 제보를 먼저 정리해야 한다.
    -- 통계 재현성이 소프트 삭제 원칙의 존재 이유다.
    pharmacy_id     BIGINT      NOT NULL REFERENCES pharmacy (id),
    drug_id         BIGINT      NOT NULL REFERENCES drug (id),
    user_id         BIGINT      REFERENCES app_user (id),  -- 시드 데이터는 NULL 허용
    price           INTEGER     NOT NULL,                  -- 원 단위
    purchased_at    DATE        NOT NULL,                  -- 구매일. 미입력 시 제출일
    source          VARCHAR(20) NOT NULL DEFAULT 'FORM',   -- FORM | SEED | RECEIPT_OCR | PARTNER
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | HIDDEN | REJECTED
    flagged         BOOLEAN     NOT NULL DEFAULT false,    -- 이상치 자동 탐지 결과
    flag_reason     VARCHAR(100),                          -- OUTLIER_HIGH | OUTLIER_LOW | DUPLICATE | MANUAL
    -- 영수증은 선택 항목이므로 파일이 지워져도 제보는 살아남아야 한다.
    receipt_file_id BIGINT      REFERENCES uploaded_file (id) ON DELETE SET NULL,
    memo            VARCHAR(200),                          -- 사용자 자유 입력
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),    -- 갱신은 JPA Auditing 이 담당한다

    -- 애플리케이션 검증만 믿지 않는다 (ROADMAP T-05 구현 가이드 6).
    CONSTRAINT ck_price_report_price  CHECK (price BETWEEN 100 AND 200000),
    CONSTRAINT ck_price_report_source CHECK (source IN ('FORM', 'SEED', 'RECEIPT_OCR', 'PARTNER')),
    -- uq_report_user_pair_day 가 status = 'ACTIVE' 에 의존하므로 오타 방지가 특히 중요하다.
    CONSTRAINT ck_price_report_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'REJECTED')),
    CONSTRAINT ck_price_report_flag_reason CHECK (
        flag_reason IS NULL
        OR flag_reason IN ('OUTLIER_HIGH', 'OUTLIER_LOW', 'DUPLICATE', 'MANUAL')
    )
);


-- -----------------------------------------------------------------------------
-- 8. pharmacy_drug_price_stat - 가격 통계 캐시
--    검색 경로에서 읽는 유일한 집계 테이블.
--    유효 제보가 0건이 되면 행을 삭제해 검색 결과에서 자동으로 빠지게 한다.
-- -----------------------------------------------------------------------------

CREATE TABLE pharmacy_drug_price_stat (
    id               BIGSERIAL   PRIMARY KEY,
    -- 집계 캐시이므로 원본이 사라지면 같이 사라져야 한다.
    pharmacy_id      BIGINT      NOT NULL REFERENCES pharmacy (id) ON DELETE CASCADE,
    drug_id          BIGINT      NOT NULL REFERENCES drug (id)     ON DELETE CASCADE,
    rep_price        INTEGER     NOT NULL,  -- 대표가격 = 유효 제보의 중앙값
    min_price        INTEGER     NOT NULL,  -- 유효 제보 최저가
    max_price        INTEGER     NOT NULL,  -- 유효 제보 최고가
    avg_price        INTEGER     NOT NULL,  -- 유효 제보 평균 (표시용)
    report_count     INTEGER     NOT NULL,  -- 유효 제보 수
    last_reported_at DATE        NOT NULL,  -- 가장 최근 유효 제보의 purchased_at. 신선도 계산 입력
    -- 계산에 사용한 창 크기. 값 집합을 IN (90, 180) 으로 묶지 않는 이유는
    -- application.yml 의 recommendation.price-window-days 가 설정값이기 때문이다.
    window_days      SMALLINT    NOT NULL,
    calculated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_stat_pair UNIQUE (pharmacy_id, drug_id),

    -- 재계산 로직(T-11)의 버그를 DB 가 막는다.
    CONSTRAINT ck_stat_window_days  CHECK (window_days > 0),
    CONSTRAINT ck_stat_report_count CHECK (report_count > 0),
    CONSTRAINT ck_stat_price_order  CHECK (min_price <= rep_price AND rep_price <= max_price),
    CONSTRAINT ck_stat_avg_price    CHECK (avg_price BETWEEN min_price AND max_price)
);


-- =============================================================================
-- 인덱스
-- =============================================================================

-- --- refresh_token -----------------------------------------------------------
-- 유효한 토큰만 찾으면 되므로 부분 인덱스로 충분하다.
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id) WHERE revoked_at IS NULL;

-- --- pharmacy ----------------------------------------------------------------
CREATE INDEX idx_pharmacy_lat_lng   ON pharmacy (lat, lng);   -- 바운딩 박스 선필터 (성능 핵심)
CREATE INDEX idx_pharmacy_region    ON pharmacy (region_code);
CREATE INDEX idx_pharmacy_name_trgm ON pharmacy USING gin (name gin_trgm_ops);  -- 약국명 부분검색

-- --- drug --------------------------------------------------------------------
CREATE INDEX idx_drug_display_name_trgm ON drug USING gin (display_name gin_trgm_ops);
CREATE INDEX idx_drug_category          ON drug (category);
CREATE INDEX idx_drug_otc               ON drug (otc_flag) WHERE otc_flag = true;

-- --- price_report ------------------------------------------------------------
-- 통계 재계산 시 가장 많이 타는 경로 (성능 핵심).
CREATE INDEX idx_report_pair_active
    ON price_report (pharmacy_id, drug_id, purchased_at DESC)
    WHERE status = 'ACTIVE' AND flagged = false;

CREATE INDEX idx_report_drug    ON price_report (drug_id, purchased_at DESC);
CREATE INDEX idx_report_user    ON price_report (user_id, created_at DESC);
CREATE INDEX idx_report_flagged ON price_report (flagged) WHERE flagged = true;

-- 중복 제보 방지 (F2-8): 같은 사용자가 같은 (약국, 약품)에 같은 날 두 번 제보하지 못한다.
--
-- created_at::date 로 쓰면 인덱스 생성 자체가 실패한다.
-- timestamptz -> date 캐스트는 세션 TimeZone 에 의존해 STABLE 인데, PostgreSQL 의
-- 인덱스 표현식은 IMMUTABLE 만 허용하기 때문이다.
-- AT TIME ZONE 'Asia/Seoul' 로 타임존을 고정하면 timestamp 가 되어
-- IMMUTABLE 캐스트가 된다. 괄호 중첩도 그대로 유지할 것.
CREATE UNIQUE INDEX uq_report_user_pair_day
    ON price_report (
        user_id, pharmacy_id, drug_id,
        ((created_at AT TIME ZONE 'Asia/Seoul')::date)
    )
    WHERE user_id IS NOT NULL AND status = 'ACTIVE';

-- --- pharmacy_drug_price_stat ------------------------------------------------
-- 검색 핵심 경로: 특정 약품의 통계 전체를 훑고 약국과 조인 (성능 핵심).
CREATE INDEX idx_stat_drug_price ON pharmacy_drug_price_stat (drug_id, rep_price);

-- uq_stat_pair (pharmacy_id, drug_id) 의 선행 컬럼과 겹쳐 사실상 중복이지만,
-- DATABASE.md §3.6 에 명시된 인덱스라 문서대로 생성한다. 제거는 문서 개정 사항.
CREATE INDEX idx_stat_pharmacy ON pharmacy_drug_price_stat (pharmacy_id);


-- =============================================================================
-- 테이블 주석 (psql \dt+ 및 DB 툴에서 바로 보인다)
-- =============================================================================

COMMENT ON TABLE region                   IS '행정구역(시군구). 위치 권한 거부 시 폴백 좌표로 사용';
COMMENT ON TABLE app_user                 IS '사용자. user 가 예약어라 app_user 를 쓴다';
COMMENT ON TABLE refresh_token            IS '리프레시 토큰 발급분. 로그아웃/회전 시 revoked_at 으로 무효화';
COMMENT ON TABLE pharmacy                 IS '약국 마스터. hira_code 가 공공데이터 재적재 멱등성 키';
COMMENT ON TABLE drug                     IS '일반의약품 마스터. 포장 단위가 다르면 별개 행';
COMMENT ON TABLE uploaded_file            IS '업로드 파일 메타데이터. 실제 파일은 stored_path 에 저장';
COMMENT ON TABLE price_report             IS '가격 제보(핵심 테이블). 물리 삭제 없이 status 로 관리';
COMMENT ON TABLE pharmacy_drug_price_stat IS '(약국, 약품) 가격 통계 캐시. 검색 경로가 읽는 유일한 집계 테이블';
