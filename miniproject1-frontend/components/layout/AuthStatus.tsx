"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { ClipboardList, Shield } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAppDispatch, useAppSelector } from "@/lib/hooks";
import { logout } from "@/lib/slices/authSlice";

/**
 * 헤더 오른쪽 위의 관리자 모드 진입점 + 로그인 상태 표시.
 *
 * "관리자 모드" 버튼은 로그인 여부와 무관하게 항상 보인다 — 비로그인
 * 상태면 로그인 화면으로 보내되 next=/admin을 붙여, 로그인에 성공하면
 * 바로 관리자 대시보드로 이어지게 한다. 로그인은 했지만 ADMIN이 아니면
 * `/admin`으로 보내도 RequireAdmin(app/admin/page.tsx)이 알아서 홈으로
 * 돌려보내므로, 여기서 role을 다시 검사할 필요가 없다.
 */
function AdminModeButton({ signedIn }: { signedIn: boolean }) {
  const adminHref = signedIn ? "/admin" : "/login?next=/admin";
  return (
    // 375px 폭에서는 텍스트를 감춰 아이콘만 보여준다(docs/ROADMAP.md T-34
    // 6번 "페이지가 가로 스크롤되면 안 된다" 검증 중 발견 — 헤더 한 줄에
    // 로고·인증 블록이 다 들어가야 해서 여유가 없다). aria-label로 좁은
    // 화면에서도 접근성 이름은 유지한다.
    <Button asChild size="sm" variant="ghost" aria-label="관리자 모드">
      <Link href={adminHref}>
        <Shield aria-hidden="true" className="size-4" />
        <span className="hidden sm:inline">관리자 모드</span>
      </Link>
    </Button>
  );
}

/**
 * 헤더에서 로그인 상태에 따라 바뀌는 조각.
 *
 * SiteHeader는 서버 컴포넌트로 유지하고, 세션을 읽어야 하는 이 부분만
 * 클라이언트 경계로 분리한다.
 */
export function AuthStatus() {
  const { user, status } = useAppSelector((state) => state.auth);
  const dispatch = useAppDispatch();
  const router = useRouter();

  if (status === "idle" || status === "loading") {
    // 세션 복원이 끝나기 전에는 로그인/닉네임 중 무엇도 단정해 보여주지
    // 않는다 — 깜빡였다 바뀌는 것을 막기 위한 자리 채우기. 관리자 모드
    // 버튼의 목적지(로그인 여부에 따라 갈림)도 아직 결정할 수 없으므로
    // 이 스켈레톤이 그 자리까지 함께 대신한다.
    return <Skeleton className="h-7 w-32 shrink-0" />;
  }

  if (!user) {
    return (
      <div className="flex shrink-0 items-center gap-2">
        <AdminModeButton signedIn={false} />
        <Button asChild size="sm" variant="outline">
          <Link href="/login">로그인</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="flex shrink-0 items-center gap-2">
      <AdminModeButton signedIn={true} />
      {/* AdminModeButton과 같은 이유로 375px에서는 텍스트를 감춘다(T-34). */}
      <Button asChild size="sm" variant="ghost" aria-label="내 제보 목록">
        <Link href="/me">
          <ClipboardList aria-hidden="true" className="size-4" />
          <span className="hidden sm:inline">내 제보</span>
        </Link>
      </Button>
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
