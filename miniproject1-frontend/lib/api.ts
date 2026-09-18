/**
 * 백엔드 REST API 클라이언트.
 *
 * docs/API.md §1.1~1.3의 응답·에러 규약을 이 파일 한 곳에서 처리한다.
 * 화면 컴포넌트는 res.ok 분기나 에러 바디 파싱을 반복하지 않고
 * try/catch로 ApiError만 다루면 된다.
 *
 * 서버 컴포넌트와 클라이언트 컴포넌트 양쪽에서 import되므로
 * window, localStorage, Redux 스토어를 직접 참조하지 않는다.
 */

/** docs/API.md §1.2 — 검증 실패일 때만 존재하는 필드별 오류 */
export interface ApiFieldError {
  field: string;
  reason: string;
}

/** docs/API.md §1.2 에러 바디 */
interface ApiErrorBody {
  code?: unknown;
  message?: unknown;
  fieldErrors?: unknown;
  traceId?: unknown;
}

/**
 * API 호출 실패를 나타내는 예외.
 *
 * 4xx/5xx 응답은 물론, 에러 바디 파싱에 실패한 경우에도 항상 이 타입으로
 * 던진다. 호출부가 예외 타입을 하나만 알면 되도록 하기 위한 것이다.
 */
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fieldErrors?: ApiFieldError[],
    public traceId?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

/** 응답이 JSON이 아니거나 code가 없을 때 쓰는 기본 코드 (docs/API.md §1.3) */
const FALLBACK_ERROR_CODE = "INTERNAL_ERROR";

/** fetch() 자체가 실패했을 때(오프라인, 서버 다운, CORS 등) 쓰는 코드 */
const NETWORK_ERROR_CODE = "NETWORK_ERROR";
const NETWORK_ERROR_MESSAGE = "네트워크 연결을 확인해 주세요.";

/**
 * fetch()를 감싸 실패를 항상 ApiError로 변환한다.
 *
 * fetch()는 응답을 받기 전 단계(오프라인, DNS 실패, CORS 차단 등)에서
 * "Failed to fetch" 같은 영어 TypeError를 던진다. 이 예외는 toApiError가
 * 다루는 Response가 아예 없어 그대로 두면 화면에 영어 원문이 노출될 수
 * 있다(docs/ROADMAP.md T-36). status 0으로 표시해 실제 서버 응답과 구분한다.
 */
async function safeFetch(input: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(input, init);
  } catch {
    throw new ApiError(0, NETWORK_ERROR_CODE, NETWORK_ERROR_MESSAGE);
  }
}

/** status별 기본 메시지. 서버가 에러 바디를 주지 못한 경우에만 쓰인다. */
const FALLBACK_MESSAGES: Record<number, string> = {
  400: "요청 값을 확인해 주세요.",
  401: "로그인이 필요합니다.",
  403: "접근 권한이 없습니다.",
  404: "요청한 정보를 찾을 수 없습니다.",
  409: "이미 처리된 요청입니다.",
  413: "파일 용량이 너무 큽니다.",
  415: "지원하지 않는 파일 형식입니다.",
  422: "처리할 수 없는 요청입니다.",
  500: "서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.",
};

function fallbackMessage(status: number): string {
  return (
    FALLBACK_MESSAGES[status] ??
    (status >= 500
      ? "서버에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
      : "요청을 처리할 수 없습니다.")
  );
}

/**
 * Access 토큰 공급자.
 *
 * api.ts는 서버 컴포넌트에서도 import되므로 Redux 스토어를 직접 참조할 수
 * 없다. 대신 이 주입 지점을 두고, app/providers.tsx에서 authSlice를 연결한다.
 * 연결 전에는 항상 null을 돌려주므로 auth: true를 붙여도 헤더만 생략된다.
 */
let accessTokenProvider: () => string | null = () => null;

export function setAccessTokenProvider(provider: () => string | null): void {
  accessTokenProvider = provider;
}

/**
 * 401 인터셉터가 쓰는 갱신 공급자.
 *
 * 이 함수도 같은 이유로 store를 직접 참조하지 않는다. app/providers.tsx가
 * GET /api/auth/session(세션 복원·rotation)을 호출하는 함수를 연결하고,
 * 성공하면 새 accessToken을, 실패하면 null을 돌려준다.
 */
let refreshHandler: (() => Promise<string | null>) | null = null;

export function setRefreshHandler(
  handler: (() => Promise<string | null>) | null,
): void {
  refreshHandler = handler;
}

function baseUrl(): string {
  // .env.local이 없어도 로컬 개발이 되도록 기본값을 둔다.
  // 환경변수 준비 절차는 README.md 참고.
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
}

function resolveUrl(path: string): string {
  if (/^https?:\/\//.test(path)) return path;
  return `${baseUrl().replace(/\/$/, "")}/${path.replace(/^\//, "")}`;
}

function toFieldErrors(value: unknown): ApiFieldError[] | undefined {
  if (!Array.isArray(value)) return undefined;
  const parsed = value.filter(
    (item): item is ApiFieldError =>
      typeof item === "object" &&
      item !== null &&
      typeof (item as ApiFieldError).field === "string" &&
      typeof (item as ApiFieldError).reason === "string",
  );
  return parsed.length > 0 ? parsed : undefined;
}

/**
 * 실패 응답을 ApiError로 변환한다.
 *
 * 바디가 JSON이 아니어도(Spring Security가 HTML 에러 페이지를 돌려줄 수
 * 있다) 반드시 ApiError를 던진다.
 */
async function toApiError(res: Response): Promise<ApiError> {
  let body: ApiErrorBody | null = null;
  try {
    body = (await res.json()) as ApiErrorBody;
  } catch {
    body = null;
  }

  const code = typeof body?.code === "string" ? body.code : FALLBACK_ERROR_CODE;
  const message =
    typeof body?.message === "string" && body.message.length > 0
      ? body.message
      : fallbackMessage(res.status);
  const traceId = typeof body?.traceId === "string" ? body.traceId : undefined;

  return new ApiError(
    res.status,
    code,
    message,
    toFieldErrors(body?.fieldErrors),
    traceId,
  );
}

export type ApiFetchInit = RequestInit & {
  /** true면 Authorization: Bearer 헤더를 붙인다 */
  auth?: boolean;
  /** 내부용 — 401 재시도 1회 제한 플래그. 직접 넘기지 않는다 */
  _retried?: boolean;
};

/**
 * 백엔드 API를 호출한다.
 *
 * @param path `/api/v1/...` 형태의 경로 또는 절대 URL
 * @returns 성공 시 파싱된 JSON. 204 No Content면 undefined
 * @throws {ApiError} 4xx/5xx 응답인 경우. 바디 파싱 실패 시에도 던진다
 */
export async function apiFetch<T>(
  path: string,
  init?: ApiFetchInit,
): Promise<T> {
  const { auth, headers, _retried, ...rest } = init ?? {};

  const finalHeaders = new Headers(headers);
  if (!finalHeaders.has("Accept")) {
    finalHeaders.set("Accept", "application/json");
  }
  // FormData는 브라우저가 boundary를 포함한 Content-Type을 직접 설정해야
  // 하므로 건드리지 않는다 (T-27 영수증 업로드).
  if (
    rest.body !== undefined &&
    !(rest.body instanceof FormData) &&
    !finalHeaders.has("Content-Type")
  ) {
    finalHeaders.set("Content-Type", "application/json");
  }
  if (auth) {
    const token = accessTokenProvider();
    if (token) finalHeaders.set("Authorization", `Bearer ${token}`);
  }

  const res = await safeFetch(resolveUrl(path), { ...rest, headers: finalHeaders });

  if (res.status === 401 && auth && !_retried && refreshHandler) {
    // body가 문자열/undefined일 때만 재시도한다. FormData·스트림은 이미
    // 한 번 소비된 뒤라 그대로 재사용하면 빈 본문으로 나가기 때문이다.
    const canRetryBody = rest.body === undefined || typeof rest.body === "string";
    if (canRetryBody) {
      const newToken = await refreshHandler();
      if (newToken) {
        return apiFetch<T>(path, { ...init, auth, _retried: true });
      }
    }
  }

  if (!res.ok) {
    throw await toApiError(res);
  }

  // 204 No Content에 res.json()을 부르면 터진다.
  // Content-Length: 0 인 200 응답도 같이 걸러낸다.
  if (res.status === 204 || res.headers.get("Content-Length") === "0") {
    return undefined as T;
  }

  return (await res.json()) as T;
}

/**
 * 인증이 필요한 바이너리(이미지 등) 응답을 받는다.
 *
 * `GET /api/v1/uploads/{fileId}`(영수증 원본, T-27)처럼 access 토큰이 필요한
 * 파일은 `<img src>`로 못 그린다 — 이 앱은 토큰을 쿠키가 아닌 메모리에만 두므로
 * 브라우저가 Authorization 헤더를 자동으로 붙여주지 않는다. 대신 이 함수로
 * Blob을 받아 `URL.createObjectURL`로 바꿔 쓴다(T-33 관리자 제보 영수증 썸네일).
 * 401 재시도는 apiFetch와 달리 하지 않는다 — 썸네일은 보조 정보라 실패하면
 * 자리표시자만 보여주면 된다.
 */
export async function apiFetchBlob(
  path: string,
  init?: ApiFetchInit,
): Promise<Blob> {
  const { auth, headers, ...rest } = init ?? {};

  const finalHeaders = new Headers(headers);
  if (auth) {
    const token = accessTokenProvider();
    if (token) finalHeaders.set("Authorization", `Bearer ${token}`);
  }

  const res = await safeFetch(resolveUrl(path), { ...rest, headers: finalHeaders });

  if (!res.ok) {
    throw await toApiError(res);
  }

  return res.blob();
}
