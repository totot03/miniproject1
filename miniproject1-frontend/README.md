# miniproject1-frontend

약값알림 프론트엔드. Next.js 16 (App Router) + React 19 + TypeScript strict + Tailwind CSS v4 + shadcn/ui.

요구사항은 [docs/PRD.md](../docs/PRD.md), API 규약은 [docs/API.md](../docs/API.md), 작업 계획은 [docs/ROADMAP.md](../docs/ROADMAP.md)를 참고한다.

---

## 실행

> ⚠️ **Windows에서는 PowerShell 또는 CMD를 쓴다.** Git Bash(MINGW64)에서 npm을 실행하면 `npm error spawn C:\Windows\system32\cmd.exe ENOENT`가 난다. npm이 내부적으로 `cmd.exe`를 띄우는데 MINGW64 환경에서 `ComSpec`/`PATH`가 깨져 있기 때문이다. `npm run dev`를 포함한 모든 npm 명령에서 재발하므로 터미널을 PowerShell로 통일하는 편이 낫다.

```powershell
npm install
npm run dev        # http://localhost:3000
```

| 스크립트 | 설명 |
|---|---|
| `npm run dev` | 개발 서버 (Turbopack이 기본, 출력은 `.next/dev`) |
| `npm run build` | 프로덕션 빌드 |
| `npm run lint` | ESLint. Next.js 16에서 `next lint`가 제거돼 ESLint CLI를 직접 부른다 |
| `npm run typecheck` | `next typegen` → `tsc --noEmit` |
| `npm run test` | Vitest 1회 실행 |
| `npm run test:watch` | Vitest watch 모드 |
| `npm run gen:api` | OpenAPI 스펙에서 `types/api.ts` 생성 (백엔드 기동 필요) |

`typecheck`가 `next typegen`을 먼저 돌리는 이유가 있다. `LayoutProps<"/">`, `PageProps<"/search">` 같은 전역 타입 헬퍼는 소스에 없고 `next typegen`이 `.next/`에 생성한다. 저장소를 새로 클론한 뒤 `tsc --noEmit`만 실행하면 타입을 찾지 못해 실패한다.

---

## 환경변수

**Next.js는 저장소 루트의 `.env`를 읽지 않는다.** 이 폴더의 `.env.local`만 읽는다. 루트 [`.env.example`](../.env.example)의 `NEXT_PUBLIC_*` 키를 이 폴더로 옮겨 적는다.

```powershell
# miniproject1-frontend/.env.local
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_KAKAO_MAP_KEY=
```

- `.env.local`은 루트 `.gitignore`의 `.env*` 패턴에 걸려 추적되지 않는다. 필요한 키 목록의 정본은 루트 `.env.example` 하나다.
- `NEXT_PUBLIC_API_BASE_URL`이 없어도 `lib/api.ts`가 `http://localhost:8080`으로 폴백하므로 로컬 개발은 된다.
- `NEXT_PUBLIC_KAKAO_MAP_KEY`는 T-22(카카오맵)까지는 비워둬도 된다.

---

## 상태 관리 경계

도구가 넷이라 겹치기 쉽다. [docs/PRD.md](../docs/PRD.md) §4.2의 기준을 그대로 따른다.

| 상태 종류 | 도구 | 예 |
|---|---|---|
| 초기 렌더에 필요한 서버 데이터 | **서버 컴포넌트에서 직접 fetch** | 검색 결과(`/search`), 약국 상세 |
| 클라이언트에서 다시 불러오는 서버 데이터 | **TanStack Query** | 약품 자동완성, 가격 이력, 제보 후 갱신, 관리자 통계 |
| 화면 간 공유되는 클라이언트 상태 | **Redux Toolkit** | 사용자 위치(`locationSlice`), 인증 세션(`authSlice`), 제보 폼 임시 입력(`reportDraftSlice`) |
| URL에 남아야 하는 상태 | **URL 쿼리** | `drugId`, `lat`, `lng`, `radius`, `sort` — 공유·뒤로가기 동작 |
| 한 컴포넌트 안에서만 쓰는 상태 | `useState` | 드롭다운 열림, 아코디언 펼침 |

> ⚠️ **검색 결과를 TanStack Query로 옮기지 않는다.** 서버 컴포넌트 SSR + URL 쿼리가 이미 그 역할을 한다. 둘 다 쓰면 데이터가 두 곳에 생긴다.

> ⚠️ **서버 컴포넌트에서 `useAppSelector`나 `useQuery`를 부르면 터진다.** `app/providers.tsx` 아래의 `"use client"` 컴포넌트에서만 쓴다.

Redux 스토어와 QueryClient는 `app/providers.tsx`에서 **마운트 단위로** 만든다. 모듈 스코프에서 한 번만 만들면 Next.js 서버가 프로세스 하나로 모든 요청을 처리하기 때문에 요청 간 상태가 섞여, 인증 세션이 다른 사용자에게 노출될 수 있다.

---

## 디렉터리 구조

```
app/                 라우트 (App Router)
  layout.tsx         루트 레이아웃 — 헤더 / 고지 배너 / 푸터
  providers.tsx      QueryClient + Redux Provider ("use client")
  _showcase/         홈 쇼케이스용 임시 컴포넌트 (T-16에서 삭제)
components/
  ui/                shadcn/ui 생성물. 직접 수정 가능하지만 재생성 시 덮어써진다
  layout/            SiteHeader, DisclaimerBanner, SiteFooter
  common/            PriceTag, DistanceBadge, EmptyState, ErrorState, LoadingSkeleton
hooks/               TanStack Query 커스텀 훅 (T-17부터)
lib/
  api.ts             apiFetch + ApiError
  format.ts          formatPrice / formatDistance / formatRelativeDate
  store.ts           makeStore 팩토리 + RootState / AppDispatch 타입
  hooks.ts           타입 지정된 useAppDispatch / useAppSelector
  slices/            locationSlice, authSlice, reportDraftSlice
  utils.ts           cn (shadcn 생성물)
types/               openapi-typescript 생성 타입
__tests__/           Vitest (node 환경)
```

### 고지 배너

`DisclaimerBanner`는 **루트 레이아웃에만** 둔다. 페이지마다 넣으면 새 라우트를 추가할 때 빠뜨린다. [docs/PRD.md](../docs/PRD.md) §9의 필수 요구사항이므로 페이지 단위로 내리지 않는다.

---

## API 클라이언트

`lib/api.ts`의 `apiFetch`를 쓰고 `fetch`를 직접 부르지 않는다. [docs/API.md](../docs/API.md) §1.2 에러 바디 파싱이 이 한 곳에 모여 있다.

```typescript
import { apiFetch, ApiError } from "@/lib/api";

try {
  const result = await apiFetch<SearchResponse>("/api/v1/search?drugId=1&lat=37.5&lng=127.0");
} catch (error) {
  if (error instanceof ApiError) {
    // error.status / error.code / error.message / error.fieldErrors / error.traceId
  }
}
```

- 실패 응답은 **항상** `ApiError`로 던진다. 바디가 JSON이 아니어도(Spring Security가 HTML 에러 페이지를 돌려줄 수 있다) 마찬가지다.
- `204 No Content`는 `undefined`를 반환한다.
- `{ auth: true }`를 주면 `Authorization: Bearer` 헤더를 붙인다. 토큰 공급자 연결은 T-25(`setAccessTokenProvider`)에서 한다.

---

## 타입 생성

**응답 타입을 수기로 정의하지 않는다.** 백엔드 OpenAPI 스펙에서 생성한다.

```powershell
# 백엔드를 먼저 띄운다: cd ../miniproject1-backend; .\mvnw.cmd spring-boot:run
npm run gen:api
```

`types/api.ts`는 아직 없다. 백엔드에 도메인 엔드포인트가 T-13부터 생기므로, 그 전에 실행하면 빈 스펙만 나온다.

---

## 스타일

Tailwind CSS v4다. **`tailwind.config.ts`가 없다** — `postcss.config.mjs`와 `app/globals.css`의 `@theme inline` 블록이 설정 전부다. v3 기준 가이드(config 파일 수정)를 그대로 따르면 안 된다.

- **라이트 단일 테마**다. `globals.css`에 `.dark` 정의는 남아 있지만 `<html>`에 `dark` 클래스를 붙이지 않는다.
- 폰트는 Geist(라틴) + 한글 시스템 폰트 스택. 한글 웹폰트를 추가로 내려받지 않는다.
- 가격·절감액·신선도 색은 `--color-price`, `--color-price-lowest`, `--color-saving`, `--color-stale` 토큰을 쓴다. 컴포넌트에 `text-red-600` 류를 직접 쓰지 않는다.
- [docs/PRD.md](../docs/PRD.md) §8에 따라 **색상만으로 정보를 전달하지 않는다.** 뱃지에는 항상 텍스트를 함께 둔다.

---

## 주의: Next.js 16

이 버전은 학습 데이터의 Next.js와 다르다. `AGENTS.md`가 `node_modules/next/dist/docs/`를 먼저 읽으라고 안내하는 이유다. 실제로 걸리는 차이:

- **Turbopack이 기본.** `--turbopack` 플래그가 필요 없다.
- **`next lint` 제거.** ESLint CLI를 직접 부른다.
- **`params` / `searchParams` / `cookies()` / `headers()`가 전부 async 전용.** 동기 접근은 불가능하다.
- **`middleware` → `proxy`** 로 이름이 바뀌었다.
- 전역 타입 헬퍼(`LayoutProps` / `PageProps` / `RouteContext`)는 `next typegen` 산출물이다.

`AGENTS.md`와 `CLAUDE.md`는 `next dev`가 관리하는 파일이라 직접 수정하지 않는다.
