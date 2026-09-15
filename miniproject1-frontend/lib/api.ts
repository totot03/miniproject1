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
 * 없다. 대신 이 주입 지점을 두고, T-25에서 authSlice를 연결한다.
 * 연결 전에는 항상 null을 돌려주므로 auth: true를 붙여도 헤더만 생략된다.
 */
let accessTokenProvider: () => string | null = () => null;

export function setAccessTokenProvider(provider: () => string | null): void {
  accessTokenProvider = provider;
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
  /** true면 Authorization: Bearer 헤더를 붙인다 (토큰 연결은 T-25) */
  auth?: boolean;
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
  const { auth, headers, ...rest } = init ?? {};

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

  const res = await fetch(resolveUrl(path), { ...rest, headers: finalHeaders });

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
