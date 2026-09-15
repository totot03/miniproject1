import Link from "next/link";
import { MapPin, Pill } from "lucide-react";

import { Button } from "@/components/ui/button";

/**
 * 전역 헤더 — 로고 / 위치 표시 / 로그인.
 *
 * 서버 컴포넌트로 둔다. 위치 표시와 로그인 상태는 클라이언트 상태지만,
 * 지금은 정적 플레이스홀더라 클라이언트 경계를 만들 이유가 없다.
 *
 * - 위치 표시: T-16에서 locationSlice를 읽는 클라이언트 컴포넌트로 교체
 * - 로그인 링크: T-25에서 authSlice를 읽어 로그인/로그아웃 전환
 */
export function SiteHeader() {
  return (
    <header className="bg-background border-b">
      <div className="mx-auto flex max-w-5xl items-center gap-3 px-4 py-3">
        <Link
          href="/"
          className="flex items-center gap-2 font-semibold tracking-tight"
        >
          <Pill aria-hidden="true" className="text-primary size-5" />
          <span className="text-base sm:text-lg">약값알림</span>
        </Link>

        {/* TODO(T-16): locationSlice 기반 위치 표시·재설정으로 교체 */}
        <span className="text-muted-foreground ml-auto flex items-center gap-1 text-xs sm:text-sm">
          <MapPin aria-hidden="true" className="size-4 shrink-0" />
          <span className="max-w-[9rem] truncate sm:max-w-none">
            위치 설정 필요
          </span>
        </span>

        {/* TODO(T-25): 로그인 상태에 따라 로그아웃·내 제보로 전환 */}
        <Button asChild size="sm" variant="outline">
          <Link href="/login">로그인</Link>
        </Button>
      </div>
    </header>
  );
}
