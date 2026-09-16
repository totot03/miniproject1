import Link from "next/link";
import { Pill } from "lucide-react";

import { LocationIndicator } from "@/components/location/LocationIndicator";
import { Button } from "@/components/ui/button";

/**
 * 전역 헤더 — 로고 / 위치 표시 / 로그인.
 *
 * 서버 컴포넌트로 둔다. 위치 표시는 `LocationIndicator`(T-16)만 클라이언트
 * 경계로 감싸 처리하고, 로고·로그인 버튼은 그대로 서버에서 렌더링한다.
 *
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

        <LocationIndicator />

        {/* TODO(T-25): 로그인 상태에 따라 로그아웃·내 제보로 전환 */}
        <Button asChild size="sm" variant="outline">
          <Link href="/login">로그인</Link>
        </Button>
      </div>
    </header>
  );
}
