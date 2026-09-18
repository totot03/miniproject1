"use client";

import { CircleAlert, RotateCcw } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { getErrorMessage } from "@/lib/error-message";
import { cn } from "@/lib/utils";

export interface ErrorStateProps {
  /** ApiError면 서버 메시지를, 그 외에는 일반 문구를 보여준다 */
  error?: unknown;
  /** error가 없거나 메시지가 없을 때 쓸 문구 */
  fallbackMessage?: string;
  /** 재시도 핸들러. 이 콜백 때문에 클라이언트 컴포넌트다 */
  onRetry?: () => void;
  className?: string;
}

const DEFAULT_MESSAGE = "정보를 불러오지 못했습니다.";

/**
 * 에러 표시 + 재시도.
 *
 * traceId는 서버 로그와 대조할 수 있도록 함께 노출한다 (docs/API.md §1.2).
 * 사용자에게는 의미가 없지만, 데모 중 문제가 생겼을 때 이 값 하나로
 * 서버 로그를 찾을 수 있다.
 */
export function ErrorState({
  error,
  fallbackMessage = DEFAULT_MESSAGE,
  onRetry,
  className,
}: ErrorStateProps) {
  const message = getErrorMessage(error, fallbackMessage);
  const traceId = error instanceof ApiError ? error.traceId : undefined;
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : undefined;

  return (
    <div
      role="alert"
      className={cn(
        "border-destructive/30 bg-destructive/5 flex flex-col items-center gap-3 rounded-lg border px-6 py-10 text-center",
        className,
      )}
    >
      <CircleAlert aria-hidden="true" className="text-destructive size-8" />
      <div className="space-y-1">
        <p className="font-medium">{message}</p>
        {fieldErrors && fieldErrors.length > 0 ? (
          <ul className="text-muted-foreground text-sm">
            {fieldErrors.map((fieldError) => (
              <li key={fieldError.field}>
                {fieldError.field}: {fieldError.reason}
              </li>
            ))}
          </ul>
        ) : null}
        {traceId ? (
          <p className="text-muted-foreground font-mono text-xs">
            traceId: {traceId}
          </p>
        ) : null}
      </div>
      {onRetry ? (
        <Button variant="outline" size="sm" onClick={onRetry}>
          <RotateCcw aria-hidden="true" className="size-4" />
          다시 시도
        </Button>
      ) : null}
    </div>
  );
}
