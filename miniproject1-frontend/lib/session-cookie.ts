/**
 * refresh 토큰을 담는 httpOnly 쿠키의 이름·옵션을 한 곳에 정의한다.
 *
 * app/api/auth/session/route.ts(쿠키 쓰기·읽기)와 proxy.ts(존재 여부만 확인)가
 * 같은 이름을 참조해야 하므로 분리했다. localStorage에는 절대 두지 않는다
 * (docs/ROADMAP.md T-25).
 */
export const SESSION_COOKIE_NAME = "refresh_token";

/** PRD.md §5 — refresh 토큰 만료 14일과 맞춘다 */
const SESSION_COOKIE_MAX_AGE_SECONDS = 14 * 24 * 60 * 60;

/** next/headers의 cookies().set(name, value, options) 세 번째 인자와 같은 모양 */
interface SessionCookieOptions {
  httpOnly: boolean;
  secure: boolean;
  sameSite: "lax";
  path: string;
  maxAge: number;
}

/**
 * 로컬 개발은 http://localhost이므로 secure를 강제하면 쿠키가 아예 안 심긴다.
 * 프로덕션에서만 secure를 켠다.
 */
export function sessionCookieOptions(): SessionCookieOptions {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: SESSION_COOKIE_MAX_AGE_SECONDS,
  };
}
