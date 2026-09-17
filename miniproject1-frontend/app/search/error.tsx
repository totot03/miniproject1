"use client";

import Link from "next/link";

import { ErrorState } from "@/components/common/ErrorState";
import { Button } from "@/components/ui/button";

/**
 * /search 에러 경계 (Next.js error.js 파일 컨벤션, "use client" 필수).
 *
 * apiFetch가 app/search/page.tsx에서 던지는 ApiError가 여기로 전달된다.
 * `retry`는 Next.js 16.3부터 안정화된 prop으로, 세그먼트를 다시
 * fetch·렌더링한다(node_modules/next/dist/docs/01-app/03-api-reference/
 * 03-file-conventions/error.md — "In most cases, you should use retry()
 * instead [of reset()]").
 *
 * drugId가 아예 없는 등 재시도로 해결되지 않는 경우도 있어 홈으로
 * 돌아가는 링크를 함께 둔다.
 */
export default function SearchError({
  error,
  retry,
}: {
  error: Error & { digest?: string };
  retry: () => void;
}) {
  return (
    <div className="mx-auto max-w-5xl space-y-4 px-4 py-8">
      <ErrorState
        error={error}
        fallbackMessage="검색 결과를 불러오지 못했습니다."
        onRetry={retry}
      />
      <div className="flex justify-center">
        <Button asChild variant="outline" size="sm">
          <Link href="/">홈으로 돌아가기</Link>
        </Button>
      </div>
    </div>
  );
}
