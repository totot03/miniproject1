import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

import { SESSION_COOKIE_NAME } from "@/lib/session-cookie";

/**
 * 보호 경로 optimistic 체크.
 *
 * Next.js 16에서 middleware.ts는 폐기되고 proxy.ts로 이름이 바뀌었다
 * (node_modules/next/dist/docs/.../file-conventions/proxy.md). docs/ROADMAP.md
 * T-25는 옛 이름(middleware)으로 적혀 있지만 실제로는 동작하지 않는다.
 *
 * access 토큰은 메모리에만 있어 proxy에서 볼 수 없다. 여기서는 refresh
 * 쿠키의 "존재 여부"만 확인하는 optimistic 체크만 한다 — 쿠키가 만료·회전
 * 되었는지까지는 검증하지 않는다(그건 각 화면의 API 호출이 401로 알려준다).
 * proxy에서 백엔드를 호출하지 않는 것은 Next.js 공식 인증 가이드의 권장
 * 사항이다: proxy는 느린 조회를 하는 자리가 아니다.
 */
const PROTECTED_PREFIXES = ["/reports/new", "/me", "/admin"];

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const isProtected = PROTECTED_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
  if (!isProtected) {
    return NextResponse.next();
  }

  if (request.cookies.has(SESSION_COOKIE_NAME)) {
    return NextResponse.next();
  }

  const loginUrl = new URL("/login", request.url);
  loginUrl.searchParams.set("next", pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ["/reports/new/:path*", "/me/:path*", "/admin/:path*"],
};
