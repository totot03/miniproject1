import { SearchX } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

export interface EmptyStateProps {
  title: string;
  description?: string;
  /** 기본 아이콘을 대체할 요소 */
  icon?: ReactNode;
  /** "반경을 5km로 넓혀보기" 같은 다음 행동 (docs/API.md §5 suggestion) */
  action?: ReactNode;
  className?: string;
}

/**
 * 결과가 없을 때의 표시.
 *
 * 검색 결과 0건은 에러가 아니라 정상 응답이므로(docs/API.md §5) ErrorState와
 * 구분해 쓴다. 반경 확대 제안처럼 다음 행동을 함께 두는 것이 중요하다.
 */
export function EmptyState({
  title,
  description,
  icon,
  action,
  className,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-12 text-center",
        className,
      )}
    >
      <div className="text-muted-foreground" aria-hidden="true">
        {icon ?? <SearchX className="size-8" />}
      </div>
      <div className="space-y-1">
        <p className="font-medium">{title}</p>
        {description ? (
          <p className="text-muted-foreground mx-auto max-w-sm text-sm leading-relaxed">
            {description}
          </p>
        ) : null}
      </div>
      {action}
    </div>
  );
}
