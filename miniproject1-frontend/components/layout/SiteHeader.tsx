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
        {/* 로고·인증 블록은 shrink-0로 고정한다 — LocationIndicator의 truncate
            라벨만 유연하게 좁아지는 요소다. shrink-0가 없으면 좁은 화면에서
            flex가 이 항목들을 내용보다 좁게 찌그러뜨려 글자가 한 자씩
            세로로 줄바꿈되는 버그가 난다(T-34 375px 검증 중 실측). */}
        <Link
          href="/"
          className="flex shrink-0 items-center gap-2 font-semibold tracking-tight"
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
