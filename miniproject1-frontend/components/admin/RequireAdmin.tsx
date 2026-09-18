"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import type { ReactNode } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { useAppSelector } from "@/lib/hooks";

/**
 * `/admin`, `/admin/reports` 공용 ADMIN 권한 가드 (docs/ROADMAP.md T-33 5번).
 *
 * proxy.ts는 refresh 쿠키의 존재 여부만 보고 비로그인 접근을 /login으로
 * 돌려보낸다 — access 토큰과 role은 메모리(Redux)에만 있어 proxy에서 볼 수
 * 없기 때문이다. 따라서 "로그인은 했지만 ADMIN이 아닌" 경우는 세션 복원이
 * 끝난 뒤 여기서 걸러 홈으로 돌려보낸다. status가 idle/loading인 동안은
 * AuthStatus.tsx와 같은 이유로 아무것도 단정하지 않고 스켈레톤만 보여준다 —
 * 그렇지 않으면 일반 사용자에게 대시보드가 잠깐 보였다 사라지는 깜빡임이 생긴다.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { user, status } = useAppSelector((state) => state.auth);

  const resolved = status === "authenticated" || status === "unauthenticated";
  const isAdmin = user?.role === "ADMIN";

  useEffect(() => {
    if (resolved && !isAdmin) {
      router.replace("/");
    }
  }, [resolved, isAdmin, router]);

  if (!resolved || !isAdmin) {
    return (
      <div className="mx-auto max-w-5xl space-y-4 px-4 py-8">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  return <>{children}</>;
}
