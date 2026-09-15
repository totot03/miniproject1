import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export interface LoadingSkeletonProps {
  /** 반복할 카드 수 */
  count?: number;
  className?: string;
}

/**
 * 약국 결과 카드 모양의 로딩 스켈레톤.
 *
 * 검색 결과 리스트(T-15)와 약국 상세 가격표(T-19)의 실제 레이아웃과
 * 높이를 맞춰 로딩 → 완료 전환에서 화면이 튀지 않게 한다.
 *
 * 스크린 리더에는 로딩 중임을 한 번만 알린다 — 카드 개수만큼 읽히면
 * 방해가 되므로 개별 Skeleton은 aria에서 숨긴다.
 */
export function LoadingSkeleton({
  count = 3,
  className,
}: LoadingSkeletonProps) {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-label="불러오는 중"
      className={cn("space-y-3", className)}
    >
      {Array.from({ length: count }, (_, index) => (
        <div
          key={index}
          aria-hidden="true"
          className="flex items-start gap-4 rounded-lg border p-4"
        >
          <div className="flex-1 space-y-2">
            <Skeleton className="h-5 w-32" />
            <Skeleton className="h-4 w-full max-w-64" />
            <div className="flex gap-2 pt-1">
              <Skeleton className="h-5 w-16 rounded-full" />
              <Skeleton className="h-5 w-20 rounded-full" />
            </div>
          </div>
          <Skeleton className="h-8 w-24 shrink-0" />
        </div>
      ))}
    </div>
  );
}
