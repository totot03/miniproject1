import type { ReactNode } from "react";

/** 로그인·가입 화면 공통 레이아웃 — 중앙 정렬된 좁은 카드 형태 */
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="mx-auto flex max-w-sm flex-col justify-center px-4 py-12">
      {children}
    </div>
  );
}
