import Link from "next/link";
import { Pill } from "lucide-react";

import { AuthStatus } from "@/components/layout/AuthStatus";
import { LocationIndicator } from "@/components/location/LocationIndicator";

/**
 * 전역 헤더 — 로고 / 위치 표시 / 로그인.
 *
 * 서버 컴포넌트로 둔다. 위치 표시(`LocationIndicator`, T-16)와 로그인 상태
 * 표시(`AuthStatus`, T-25)만 클라이언트 경계로 감싸 처리하고, 로고는 그대로
 * 서버에서 렌더링한다.
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

        <AuthStatus />
      </div>
    </header>
  );
}
