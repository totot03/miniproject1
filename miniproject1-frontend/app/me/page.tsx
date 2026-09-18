"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ApiError, apiFetch } from "@/lib/api";
import { formatPrice, formatRelativeDate } from "@/lib/format";
import type { components } from "@/types/api";

type PageResponse = components["schemas"]["PageResponsePriceReportListItemResponse"];
type ReportItem = components["schemas"]["PriceReportListItemResponse"];
type ReportStatus = NonNullable<ReportItem["status"]>;

const PAGE_SIZE = 20;

const STATUS_BADGE: Record<
  ReportStatus,
  { label: string; variant: "default" | "secondary" | "destructive" }
> = {
  ACTIVE: { label: "반영됨", variant: "default" },
  HIDDEN: { label: "숨김 처리됨", variant: "secondary" },
  // 스펙(docs/ROADMAP.md T-30)엔 없지만 ReportStatus enum에 존재해 방어적으로 처리한다.
  REJECTED: { label: "반려됨", variant: "destructive" },
};

/** flagged 이상치 제보는 status가 여전히 ACTIVE라, HIDDEN/REJECTED가 아닐 때만 "검토 중"을 우선 표시한다. */
function resolveStatusBadge(status: ReportStatus, flagged: boolean) {
  if (status === "ACTIVE" && flagged) {
    return { label: "검토 중", variant: "outline" as const };
  }
  return STATUS_BADGE[status];
}

/** openapi-typescript는 필수 여부를 모르면 전부 optional로 만든다 — PharmacyResultCard.tsx와 같은 패턴. */
function hasRequiredFields(item: ReportItem): item is ReportItem & {
  id: number;
  pharmacy: { name: string };
  drug: { displayName: string };
  price: number;
  purchasedAt: string;
  status: ReportStatus;
  flagged: boolean;
} {
  return (
    typeof item.id === "number" &&
    typeof item.pharmacy?.name === "string" &&
    typeof item.drug?.displayName === "string" &&
    typeof item.price === "number" &&
    typeof item.purchasedAt === "string" &&
    typeof item.status === "string" &&
    typeof item.flagged === "boolean"
  );
}

/**
 * 내 제보 목록 (docs/ROADMAP.md T-30).
 *
 * proxy.ts가 이미 비로그인 접근을 /login으로 돌려보내므로, 이 페이지 자체는
 * "로그인된 상태에서 시작"을 전제로 만든다. 다만 access 토큰이 만료되고
 * refresh까지 실패하는 경우(세션이 진짜 끊긴 경우)는 optimistic 체크로는
 * 못 잡으므로, 401을 직접 감지해 로그인으로 보낸다 (reports/new/page.tsx와 동일한 패턴).
 */
export default function MyReportsPage() {
  const router = useRouter();
  const [page, setPage] = useState(0);

  const query = useQuery({
    queryKey: ["my-price-reports", page],
    queryFn: () =>
      apiFetch<PageResponse>(
        `/api/v1/price-reports?mine=true&page=${page}&size=${PAGE_SIZE}`,
        { auth: true },
      ),
  });

  useEffect(() => {
    if (query.error instanceof ApiError && query.error.status === 401) {
      router.push("/login?next=/me");
    }
  }, [query.error, router]);

  const items = (query.data?.content ?? []).filter(hasRequiredFields);

  return (
    <div className="mx-auto max-w-lg space-y-6 px-4 py-8">
      <div className="space-y-1">
        <h1 className="text-xl font-bold tracking-tight">내 제보 목록</h1>
        {query.data ? (
          <p className="text-muted-foreground text-sm">
            총 {query.data.totalElements ?? 0}건
          </p>
        ) : null}
      </div>

      {query.isLoading ? <LoadingSkeleton count={4} /> : null}

      {query.isError ? (
        <ErrorState
          error={query.error}
          fallbackMessage="제보 목록을 불러오지 못했습니다."
          onRetry={() => query.refetch()}
        />
      ) : null}

      {query.isSuccess && items.length === 0 ? (
        <EmptyState
          title="아직 제보한 가격이 없어요"
          description="가격을 제보하면 다른 사용자에게 바로 도움이 됩니다."
          action={
            <Button asChild size="sm">
              <Link href="/reports/new">첫 가격을 제보해보세요</Link>
            </Button>
          }
        />
      ) : null}

      {items.length > 0 ? (
        <div className="space-y-3">
          {items.map((item) => {
            const badge = resolveStatusBadge(item.status, item.flagged);
            return (
              <Card key={item.id}>
                <CardContent className="flex flex-wrap items-start justify-between gap-4">
                  <div className="min-w-0 flex-1 space-y-1.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="truncate font-semibold">{item.pharmacy.name}</h3>
                      <Badge variant={badge.variant}>{badge.label}</Badge>
                    </div>
                    <p className="text-muted-foreground truncate text-sm">
                      {item.drug.displayName}
                      {item.drug.packageUnit ? ` · ${item.drug.packageUnit}` : ""}
                    </p>
                    <p className="text-muted-foreground text-xs">
                      {formatRelativeDate(item.purchasedAt)} 구매
                    </p>
                  </div>
                  <p className="shrink-0 font-semibold">{formatPrice(item.price)}</p>
                </CardContent>
              </Card>
            );
          })}
        </div>
      ) : null}

      {query.data && (query.data.totalPages ?? 0) > 1 ? (
        <div className="flex items-center justify-between pt-2">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((current) => current - 1)}
          >
            이전
          </Button>
          <span className="text-muted-foreground text-xs">
            {(query.data.page ?? 0) + 1} / {query.data.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={!query.data.hasNext}
            onClick={() => setPage((current) => current + 1)}
          >
            다음
          </Button>
        </div>
      ) : null}
    </div>
  );
}
