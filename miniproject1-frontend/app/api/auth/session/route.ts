import { cookies } from "next/headers";
import { NextResponse } from "next/server";

import { apiFetch, ApiError } from "@/lib/api";
import { SESSION_COOKIE_NAME, sessionCookieOptions } from "@/lib/session-cookie";

/**
 * 세션 BFF(Backend-for-Frontend).
 *
 * 브라우저 JS는 httpOnly 쿠키를 직접 쓸 수 없으므로, refreshToken은 이 라우트를
 * 거쳐서만 쿠키에 저장·회전·삭제된다. 클라이언트는 토큰 문자열을 넘긴 뒤 바로
 * 잊어버리고, accessToken만 돌려받아 Redux(메모리)에 둔다 (docs/API.md §1.4,
 * docs/ROADMAP.md T-25).
 *
 * types/api.ts는 T-24 이전에 생성돼 인증 스키마가 없다. gen:api는 백엔드+DB
 * 기동이 필요해 이 티켓 범위 밖이므로, API.md §2 스펙을 그대로 손으로 옮긴다.
 */

interface AuthUserResponse {
  id: number;
  nickname: string;
  role: "USER" | "ADMIN";
}

interface AuthSessionResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: AuthUserResponse;
}

/** 로그인 직후 클라이언트가 넘긴 refreshToken을 httpOnly 쿠키로 저장한다 */
export async function POST(request: Request) {
  const body = (await request.json().catch(() => null)) as
    | { refreshToken?: unknown }
    | null;

  if (typeof body?.refreshToken !== "string" || body.refreshToken.length === 0) {
    return NextResponse.json(
      { code: "VALIDATION_FAILED", message: "refreshToken이 필요합니다." },
      { status: 400 },
    );
  }

  const cookieStore = await cookies();
  cookieStore.set(SESSION_COOKIE_NAME, body.refreshToken, sessionCookieOptions());

  return new NextResponse(null, { status: 204 });
}

/**
 * 쿠키의 refreshToken으로 백엔드 /auth/refresh를 호출해 access 토큰을 갱신한다.
 * 새로고침 직후의 "세션 복원"과, lib/api.ts 401 인터셉터의 "재시도용 갱신"
 * 양쪽에서 재사용한다 — 둘 다 같은 rotation 호출이기 때문이다.
 */
export async function GET() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(SESSION_COOKIE_NAME)?.value;

  if (!refreshToken) {
    return NextResponse.json({ code: "UNAUTHENTICATED" }, { status: 401 });
  }

  try {
    const data = await apiFetch<AuthSessionResponse>("/api/v1/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    });

    cookieStore.set(SESSION_COOKIE_NAME, data.refreshToken, sessionCookieOptions());

    return NextResponse.json({
      accessToken: data.accessToken,
      expiresIn: data.expiresIn,
      user: data.user,
    });
  } catch (error) {
    // 만료·회전됨·존재하지 않음 등 어떤 이유든 더 이상 유효하지 않으므로 지운다
    cookieStore.delete(SESSION_COOKIE_NAME);
    const status = error instanceof ApiError ? error.status : 401;
    return NextResponse.json({ code: "UNAUTHENTICATED" }, { status });
  }
}

/** 로그아웃 — 백엔드 revoke 후(실패해도 무시) 쿠키를 지운다. 멱등해야 한다 */
export async function DELETE() {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get(SESSION_COOKIE_NAME)?.value;

  if (refreshToken) {
    try {
      await apiFetch("/api/v1/auth/logout", {
        method: "POST",
        body: JSON.stringify({ refreshToken }),
      });
    } catch {
      // docs/API.md §2 — 로그아웃은 실패를 알려줄 이유가 없다. 쿠키는 어차피 지운다.
    }
  }

  cookieStore.delete(SESSION_COOKIE_NAME);
  return new NextResponse(null, { status: 204 });
}
