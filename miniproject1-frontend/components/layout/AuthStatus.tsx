"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Shield } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAppDispatch, useAppSelector } from "@/lib/hooks";
import { logout } from "@/lib/slices/authSlice";

/**
 * 헤더에서 로그인 상태에 따라 바뀌는 조각.
 *
 * SiteHeader는 서버 컴포넌트로 유지하고, 세션을 읽어야 하는 이 부분만
 * 클라이언트 경계로 분리한다 — LocationIndicator와 같은 패턴이다.
 */
export function AuthStatus() {
  const { user, status } = useAppSelector((state) => state.auth);
  const dispatch = useAppDispatch();
  const router = useRouter();

  if (status === "idle" || status === "loading") {
    // 세션 복원이 끝나기 전에는 로그인/닉네임 중 무엇도 단정해 보여주지
    // 않는다 — 깜빡였다 바뀌는 것을 막기 위한 자리 채우기.
    return <Skeleton className="h-7 w-16 shrink-0" />;
  }

  if (!user) {
    return (
      <Button asChild size="sm" variant="outline" className="shrink-0">
        <Link href="/login">로그인</Link>
      </Button>
    );
  }

  return (
    <div className="flex shrink-0 items-center gap-2">
      {user.role === "ADMIN" ? (
        // 관리자 메뉴 자체를 일반 사용자에게 노출하지 않는다 — CSS로 숨기는
        // 게 아니라 조건부 렌더라 DOM에도 존재하지 않는다 (docs/ROADMAP.md T-33 5번).
        // 375px 폭에서는 텍스트를 감춰 아이콘만 보여준다(docs/ROADMAP.md T-34
        // 6번 "페이지가 가로 스크롤되면 안 된다" 검증 중 발견 — LocationIndicator의
        // 위치 라벨 truncate와 같은 이유로, 헤더 한 줄에 로고·위치·인증 3개가
        // 다 들어가야 해서 여유가 없다). aria-label로 좁은 화면에서도 접근성 이름은 유지한다.
        <Button asChild size="sm" variant="ghost" aria-label="관리자">
          <Link href="/admin">
            <Shield aria-hidden="true" className="size-4" />
            <span className="hidden sm:inline">관리자</span>
          </Link>
        </Button>
      ) : null}
      <span className="text-sm">{user.nickname}님</span>
      <Button
        size="sm"
        variant="ghost"
        onClick={() => {
          void dispatch(logout());
          router.push("/");
        }}
      >
        로그아웃
      </Button>
    </div>
  );
}
