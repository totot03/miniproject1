"use client";

import Link from "next/link";

import { ErrorState } from "@/components/common/ErrorState";
import { Button } from "@/components/ui/button";

/**
 * /pharmacies/[id] 에러 경계 (Next.js error.js 파일 컨벤션, "use client" 필수).
 *
 * apiFetch가 app/pharmacies/[id]/page.tsx에서 던지는 ApiError(404
 * PHARMACY_NOT_FOUND 포함)가 여기로 전달된다. app/search/error.tsx와 동일한
 * 패턴 — 없는 약국 ID로 들어온 경우 재시도로는 해결되지 않으므로 홈으로
 * 돌아가는 링크를 함께 둔다.
 */
export default function PharmacyDetailError({
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
        fallbackMessage="약국 정보를 불러오지 못했습니다."
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
