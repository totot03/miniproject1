"use client";

import { useState } from "react";

import { ErrorState } from "@/components/common/ErrorState";
import { ApiError } from "@/lib/api";
import { useAppSelector } from "@/lib/hooks";

/**
 * ErrorState 쇼케이스용 클라이언트 래퍼.
 *
 * 두 가지 이유로 클라이언트 컴포넌트다.
 * 1. onRetry는 함수라 서버 컴포넌트에서 props로 넘길 수 없다 (직렬화 불가)
 * 2. useAppSelector로 Provider 연결을 실제로 검증한다 (T-04 완료 판정)
 *
 * _ 접두사 폴더는 Next.js가 라우트로 만들지 않는다.
 * TODO(T-16): 홈을 검색창으로 교체할 때 이 폴더를 통째로 삭제한다.
 */
export function ShowcaseErrorState() {
  const [retryCount, setRetryCount] = useState(0);

  // Provider 아래에서 Redux 스토어가 실제로 읽히는지 확인하는 지점.
  // 서버 컴포넌트에서 이 훅을 부르면 터진다 (docs/PRD.md §4.2).
  const locationSource = useAppSelector((state) => state.location.source);

  return (
    <div className="space-y-2">
      <ErrorState
        error={
          new ApiError(
            400,
            "VALIDATION_FAILED",
            "가격은 100원 이상 200,000원 이하여야 합니다.",
            [{ field: "price", reason: "must be between 100 and 200000" }],
            "9f3a1c2e",
          )
        }
        onRetry={() => setRetryCount((count) => count + 1)}
      />
      <p className="text-muted-foreground text-xs">
        재시도 클릭 {retryCount}회 · locationSlice.source:{" "}
        <code>{locationSource ?? "null"}</code> (Redux Provider 연결 확인)
      </p>
    </div>
  );
}
