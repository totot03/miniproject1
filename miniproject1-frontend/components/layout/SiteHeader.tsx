import Link from "next/link";
import { Pill } from "lucide-react";

import { AuthStatus } from "@/components/layout/AuthStatus";

/**
 * 전역 헤더 — 로고 / 로그인.
 *
 * 서버 컴포넌트로 둔다. 로그인 상태 표시(`AuthStatus`, T-25)만 클라이언트
 * 경계로 감싸 처리하고, 로고는 그대로 서버에서 렌더링한다.
 *
 * 위치 표시·재설정(예전 LocationIndicator, T-16)은 더 이상 전역 헤더에
 * 두지 않는다 — "현재 위치 → 근처 약국"은 홈(NearbyPharmacies)이, 위치를
 * 수동으로 바꾸는 기능은 약 검색 화면(SearchLocationBar, app/search/page.tsx)이
 * 각각 전담한다.
 */
export function SiteHeader() {
  return (
    <header className="bg-background border-b">
      <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
        {/* 로고·인증 블록은 shrink-0로 고정한다(T-34 375px 검증 중 실측 —
            flex가 내용보다 좁게 찌그러뜨려 글자가 한 자씩 세로로 줄바꿈되는
            버그 방지). */}
        <Link
          href="/"
          className="flex shrink-0 items-center gap-2 font-semibold tracking-tight"
        >
          <Pill aria-hidden="true" className="text-primary size-5" />
          <span className="text-base sm:text-lg">약값알림</span>
        </Link>

        <AuthStatus />
      </div>
    </header>
  );
}
