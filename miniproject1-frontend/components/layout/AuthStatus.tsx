"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

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
    return <Skeleton className="h-7 w-16" />;
  }

  if (!user) {
    return (
      <Button asChild size="sm" variant="outline">
        <Link href="/login">로그인</Link>
      </Button>
    );
  }

  return (
    <div className="flex items-center gap-2">
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
