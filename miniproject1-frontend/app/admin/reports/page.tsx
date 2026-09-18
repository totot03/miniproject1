"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { AdminNav } from "@/components/admin/AdminNav";
import { ConfirmReportActionDialog } from "@/components/admin/ConfirmReportActionDialog";
import { RequireAdmin } from "@/components/admin/RequireAdmin";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError, apiFetch, apiFetchBlob } from "@/lib/api";
import { formatPrice, formatRelativeDate } from "@/lib/format";

const PAGE_SIZE = 20;

/**
 * docs/API.md §8 관리자 제보 목록/수정 응답.
 *
 * app/admin/page.tsx와 같은 이유로 types/api.ts 대신 백엔드 DTO
 * (AdminReportListItemResponse, AdminReportUpdateResponse)를 손으로 옮긴다.
 */
type ReportStatus = "ACTIVE" | "HIDDEN" | "REJECTED";
type FlagReason = "OUTLIER_HIGH" | "OUTLIER_LOW" | "DUPLICATE" | "MANUAL";

interface AdminReportListItem {
  id: number;
  pharmacy: { id: number; name: string };
  drug: { id: number; displayName: string; packageUnit: string | null };
  price: number;
  purchasedAt: string;
  reporter: { id: number; email: string } | null;
  status: ReportStatus;
  flagged: boolean;
  flagReason: FlagReason | null;
  receiptFileId: number | null;
}

interface AdminReportListResponse {
  content: AdminReportListItem[];
  page: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

const STATUS_BADGE: Record<ReportStatus, { label: string; variant: "default" | "secondary" | "destructive" }> = {
  ACTIVE: { label: "반영됨", variant: "default" },
  HIDDEN: { label: "숨김 처리됨", variant: "secondary" },
  REJECTED: { label: "반려됨", variant: "destructive" },
};

/** flagged 제보는 status가 여전히 ACTIVE다(T-32) — HIDDEN/REJECTED가 아닐 때만 "검토 중"을 우선 표시한다. */
function resolveStatusBadge(status: ReportStatus, flagged: boolean) {
  if (status === "ACTIVE" && flagged) {
    return { label: "검토 중", variant: "outline" as const };
  }
  return STATUS_BADGE[status];
}

const FLAG_REASON_LABEL: Record<FlagReason, string> = {
  OUTLIER_HIGH: "가격 과도하게 높음",
  OUTLIER_LOW: "가격 과도하게 낮음",
  DUPLICATE: "중복 제보",
  MANUAL: "수동 플래그",
};

type ReportPatch = { status?: ReportStatus; flagged?: boolean };

interface ReportAction {
  key: string;
  label: string;
  description: string;
  patch: ReportPatch;
}

/**
 * 행별 가능한 액션을 계산한다.
 *
 * flagged 제보는 status가 여전히 ACTIVE라("검토 중"), 정상으로 표시(unflag)와
 * 숨김 두 가지가 모두 가능하다. 이미 HIDDEN인 건은 복구만 가능하다.
 */
function resolveActions(item: AdminReportListItem): ReportAction[] {
  if (item.status === "HIDDEN") {
    return [
      {
        key: "restore",
        label: "복구",
        description: `${item.pharmacy.name}의 ${item.drug.displayName} 제보를 다시 통계에 포함시킬까요?`,
        patch: { status: "ACTIVE" },
      },
    ];
  }

  const actions: ReportAction[] = [];
  if (item.flagged) {
    actions.push({
      key: "unflag",
      label: "정상으로 표시",
      description: `${item.pharmacy.name}의 ${item.drug.displayName} 제보를 정상 제보로 표시할까요? 이상치 플래그가 해제됩니다.`,
      patch: { flagged: false },
    });
  }
  actions.push({
    key: "hide",
    label: "숨김",
    description: `${item.pharmacy.name}의 ${item.drug.displayName} 제보를 숨길까요? 해당 약국·약품의 대표가격이 즉시 재계산됩니다.`,
    patch: { status: "HIDDEN" },
  });
  return actions;
}

/** GET /api/v1/uploads/{fileId}는 인증이 필요해 <img src>로 못 그린다 — apiFetchBlob으로 받아 blob: URL을 만든다. */
function ReceiptThumbnail({ fileId }: { fileId: number | null }) {
  const [url, setUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (fileId === null) return;
    let objectUrl: string | null = null;
    let cancelled = false;

    apiFetchBlob(`/api/v1/uploads/${fileId}`, { auth: true })
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [fileId]);

  if (fileId === null) {
    return <span className="text-muted-foreground text-xs">영수증 없음</span>;
  }
  if (failed) {
    return <span className="text-muted-foreground text-xs">열람 실패</span>;
  }
  if (!url) {
    return <Skeleton className="size-12 rounded" />;
  }
  // eslint-disable-next-line @next/next/no-img-element -- blob: URL은 next/image가 최적화할 수 없다
  return <img src={url} alt="영수증" className="size-12 rounded border object-cover" />;
}

function AdminReportsContent() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const [flaggedOnly, setFlaggedOnly] = useState(true);
  const [statusFilter, setStatusFilter] = useState<ReportStatus | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const [pendingAction, setPendingAction] = useState<{ item: AdminReportListItem; action: ReportAction } | null>(
    null,
  );

  const query = useQuery({
    queryKey: ["admin-reports", flaggedOnly, statusFilter, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (flaggedOnly) params.set("flagged", "true");
      if (statusFilter !== "ALL") params.set("status", statusFilter);
      return apiFetch<AdminReportListResponse>(`/api/v1/admin/price-reports?${params}`, {
        auth: true,
      });
    },
  });

  useEffect(() => {
    if (query.error instanceof ApiError && query.error.status === 401) {
      router.push("/login?next=/admin/reports");
    }
  }, [query.error, router]);

  const updateMutation = useMutation({
    mutationFn: ({ reportId, patch, reason }: { reportId: number; patch: ReportPatch; reason: string }) =>
      apiFetch(`/api/v1/admin/price-reports/${reportId}`, {
        method: "PATCH",
        auth: true,
        body: JSON.stringify(reason ? { ...patch, reason } : patch),
      }),
    onSuccess: () => {
      toast.success("처리했습니다.");
      setPendingAction(null);
      // 목록과 대시보드 KPI(특히 flaggedReportCount) 둘 다 갱신해야 한다 —
      // QueryClient는 app/providers.tsx에서 루트에 한 번만 만들어져 라우트를
      // 이동해도 캐시가 유지되므로, 여기서 무효화하면 /admin으로 돌아갔을 때
      // 바로 최신 값이 보인다 (docs/ROADMAP.md T-33 완료 판정).
      queryClient.invalidateQueries({ queryKey: ["admin-reports"] });
      queryClient.invalidateQueries({ queryKey: ["admin-stats-overview"] });
    },
    onError: (error) => {
      toast.error(error instanceof ApiError ? error.message : "처리에 실패했습니다.");
    },
  });

  const items = query.data?.content ?? [];

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-8">
      <div className="space-y-3">
        <div className="space-y-1">
          <h1 className="text-xl font-bold tracking-tight">제보 관리</h1>
          {query.data ? (
            <p className="text-muted-foreground text-sm">총 {query.data.totalElements}건</p>
          ) : null}
        </div>
        <AdminNav />
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          variant={flaggedOnly ? "default" : "outline"}
          size="sm"
          onClick={() => {
            setFlaggedOnly((current) => !current);
            setPage(0);
          }}
        >
          플래그된 제보만
        </Button>

        <Select
          value={statusFilter}
          onValueChange={(value) => {
            setStatusFilter(value as ReportStatus | "ALL");
            setPage(0);
          }}
        >
          <SelectTrigger size="sm" className="w-36">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">전체 상태</SelectItem>
            <SelectItem value="ACTIVE">반영됨</SelectItem>
            <SelectItem value="HIDDEN">숨김 처리됨</SelectItem>
            <SelectItem value="REJECTED">반려됨</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {query.isLoading ? <LoadingSkeleton count={5} /> : null}

      {query.isError ? (
        <ErrorState
          error={query.error}
          fallbackMessage="제보 목록을 불러오지 못했습니다."
          onRetry={() => query.refetch()}
        />
      ) : null}

      {query.isSuccess && items.length === 0 ? (
        <EmptyState title="조건에 맞는 제보가 없습니다" description="필터를 바꿔서 다시 확인해 보세요." />
      ) : null}

      {items.length > 0 ? (
        <div className="space-y-3">
          {items.map((item) => {
            const badge = resolveStatusBadge(item.status, item.flagged);
            const actions = resolveActions(item);
            return (
              <Card key={item.id}>
                <CardContent className="flex flex-wrap items-start gap-4">
                  <ReceiptThumbnail fileId={item.receiptFileId} />

                  <div className="min-w-0 flex-1 space-y-1.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <h3 className="truncate font-semibold">{item.pharmacy.name}</h3>
                      <Badge variant={badge.variant}>{badge.label}</Badge>
                      {item.flagReason ? (
                        <Badge variant="destructive">{FLAG_REASON_LABEL[item.flagReason]}</Badge>
                      ) : null}
                    </div>
                    <p className="text-muted-foreground truncate text-sm">
                      {item.drug.displayName}
                      {item.drug.packageUnit ? ` · ${item.drug.packageUnit}` : ""}
                    </p>
                    <p className="text-muted-foreground text-xs">
                      {formatRelativeDate(item.purchasedAt)} 구매 ·{" "}
                      {item.reporter ? item.reporter.email : "시드 데이터"}
                    </p>
                  </div>

                  <div className="flex shrink-0 flex-col items-end gap-2">
                    <p className="font-semibold">{formatPrice(item.price)}</p>
                    <div className="flex gap-2">
                      {actions.map((action) => (
                        <Button
                          key={action.key}
                          variant="outline"
                          size="sm"
                          onClick={() => setPendingAction({ item, action })}
                        >
                          {action.label}
                        </Button>
                      ))}
                    </div>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      ) : null}

      {query.data && query.data.totalPages > 1 ? (
        <div className="flex items-center justify-between pt-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
            이전
          </Button>
          <span className="text-muted-foreground text-xs">
            {query.data.page + 1} / {query.data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={!query.data.hasNext} onClick={() => setPage((current) => current + 1)}>
            다음
          </Button>
        </div>
      ) : null}

      <ConfirmReportActionDialog
        // pendingAction이 바뀔 때마다(취소·성공 후 null → 다음 액션) 강제로
        // 리마운트해 사유 입력창을 비운다. 뮤테이션 성공 시 부모가
        // setPendingAction(null)로 "외부에서" open을 false로 만드는데, 이
        // 경로는 Radix onOpenChange를 거치지 않아 내부 state를 못 비운다.
        key={pendingAction ? `${pendingAction.item.id}-${pendingAction.action.key}` : "closed"}
        open={pendingAction !== null}
        onOpenChange={(open) => {
          if (!open) setPendingAction(null);
        }}
        title={pendingAction ? `${pendingAction.action.label} 처리` : ""}
        description={pendingAction?.action.description ?? ""}
        confirmLabel={pendingAction?.action.label ?? "확인"}
        isPending={updateMutation.isPending}
        onConfirm={(reason) => {
          if (!pendingAction) return;
          updateMutation.mutate({
            reportId: pendingAction.item.id,
            patch: pendingAction.action.patch,
            reason,
          });
        }}
      />
    </div>
  );
}

/** `/admin/reports` 이상치 제보 관리 (docs/ROADMAP.md T-33). */
export default function AdminReportsPage() {
  return (
    <RequireAdmin>
      <AdminReportsContent />
    </RequireAdmin>
  );
}
