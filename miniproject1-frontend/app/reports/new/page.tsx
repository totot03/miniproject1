"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { FormProvider, useForm } from "react-hook-form";
import { toast } from "sonner";

import { DrugPicker } from "@/components/DrugPicker";
import { PharmacyPicker } from "@/components/pharmacy-picker";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { ApiError, apiFetch } from "@/lib/api";
import { formatNumber, todayInKST } from "@/lib/format";
import { useAppDispatch, useAppSelector } from "@/lib/hooks";
import { prefillPharmacy, resetDraft, setDraft } from "@/lib/slices/reportDraftSlice";
import { MAX_PAST_DAYS, MAX_PRICE, MIN_PRICE, reportSchema, type ReportFormValues } from "@/lib/validation/report";
import type { components } from "@/types/api";

type PriceReportResponse = components["schemas"]["PriceReportResponse"];
type UploadResponse = components["schemas"]["UploadResponse"];

/** docs/API.md §6 fieldErrors가 실제로 붙을 수 있는 필드만 화이트리스트로 둔다 (SignupForm.tsx와 같은 방식). */
const REPORT_FIELDS = ["pharmacyId", "drugId", "price", "purchasedAt", "memo"] as const;

/** "-100"은 부호를 보존하고("abc"는 숫자가 없으므로 0으로) 표시용 콤마 포맷을 함께 만든다. */
function parsePriceInput(raw: string): { numeric: number; display: string } {
  const negative = raw.trim().startsWith("-");
  const digits = raw.replace(/[^0-9]/g, "");
  if (digits === "") return { numeric: 0, display: negative ? "-" : "" };
  const numeric = negative ? -Number(digits) : Number(digits);
  return { numeric, display: `${negative ? "-" : ""}${Number(digits).toLocaleString("ko-KR")}` };
}

/**
 * 가격 제보 폼 (docs/ROADMAP.md T-29).
 *
 * proxy.ts가 이미 비로그인 접근을 /login으로 돌려보내므로, 이 페이지 자체는
 * "로그인된 상태에서 시작"을 전제로 만든다. 다만 access 토큰이 만료되고
 * refresh까지 실패하는 경우(세션이 진짜 끊긴 경우)는 optimistic 체크로는
 * 못 잡으므로, 제출 시 401을 직접 감지해 로그인으로 보낸다 — 이때 입력값이
 * 날아가지 않도록 reportDraftSlice(Redux)에 계속 미러링해 둔다.
 */
export default function ReportNewPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const dispatch = useAppDispatch();
  const draft = useAppSelector((state) => state.reportDraft);

  const prefilledRef = useRef(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [flaggedResult, setFlaggedResult] = useState<{ pharmacyId: number; warning: string } | null>(null);

  const form = useForm<ReportFormValues>({
    resolver: zodResolver(reportSchema),
    defaultValues: {
      pharmacyId: draft.pharmacyId ?? 0,
      pharmacyName: draft.pharmacyName ?? "",
      drugId: draft.drugId ?? 0,
      drugName: draft.drugName ?? "",
      price: draft.price ?? 0,
      purchasedAt: draft.purchasedAt ?? todayInKST(),
      memo: draft.memo ?? "",
      uploadedFileId: draft.uploadedFileId ?? null,
    },
  });

  const [priceText, setPriceText] = useState(() => {
    const initial = draft.price ?? 0;
    return initial > 0 ? formatNumber(initial) : "";
  });

  // ?pharmacyId=&pharmacyName= 쿼리가 있으면 마운트 시 1회만 반영한다
  // (docs/ROADMAP.md T-29 3번 — 약국 상세에서 넘어올 때 미리 채움).
  useEffect(() => {
    if (prefilledRef.current) return;
    prefilledRef.current = true;
    const pharmacyIdParam = searchParams.get("pharmacyId");
    const pharmacyNameParam = searchParams.get("pharmacyName");
    const id = pharmacyIdParam ? Number(pharmacyIdParam) : NaN;
    if (Number.isFinite(id) && id > 0 && pharmacyNameParam) {
      form.setValue("pharmacyId", id, { shouldValidate: true });
      form.setValue("pharmacyName", pharmacyNameParam, { shouldValidate: true });
      dispatch(prefillPharmacy({ pharmacyId: id, pharmacyName: pharmacyNameParam }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 폼 값이 바뀔 때마다 Redux로 미러링한다 — 리다이렉트(재로그인) 왕복에도
  // 살아남게 하기 위함(reportDraftSlice.ts 참고).
  useEffect(() => {
    const subscription = form.watch((values) => {
      dispatch(
        setDraft({
          pharmacyId: values.pharmacyId || null,
          pharmacyName: values.pharmacyName || null,
          drugId: values.drugId || null,
          drugName: values.drugName || null,
          price: values.price || null,
          purchasedAt: values.purchasedAt || null,
          memo: values.memo || null,
          uploadedFileId: values.uploadedFileId ?? null,
        }),
      );
    });
    return () => subscription.unsubscribe();
  }, [form, dispatch]);

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const body = new FormData();
      body.append("file", file);
      body.append("purpose", "RECEIPT");
      return apiFetch<UploadResponse>("/api/v1/uploads", { method: "POST", auth: true, body });
    },
    onSuccess: (data) => {
      if (typeof data.id === "number") {
        form.setValue("uploadedFileId", data.id, { shouldValidate: true });
      }
    },
  });

  const submitMutation = useMutation({
    mutationFn: (values: ReportFormValues) =>
      apiFetch<PriceReportResponse>("/api/v1/price-reports", {
        method: "POST",
        auth: true,
        body: JSON.stringify({
          pharmacyId: values.pharmacyId,
          drugId: values.drugId,
          price: values.price,
          purchasedAt: values.purchasedAt,
          receiptFileId: values.uploadedFileId ?? undefined,
          memo: values.memo || undefined,
        }),
      }),
  });

  function goToPharmacyDetail(pharmacyId: number) {
    dispatch(resetDraft());
    router.push(`/pharmacies/${pharmacyId}`);
    // 약국 상세는 서버 컴포넌트라 TanStack Query 캐시를 쓰지 않는다 — 새
    // 대표가격을 즉시 보여주려면 라우트 캐시를 무시하고 다시 렌더해야 한다.
    router.refresh();
  }

  const onSubmit = form.handleSubmit(async (values) => {
    try {
      const result = await submitMutation.mutateAsync(values);
      if (result.flagged && result.warning) {
        setFlaggedResult({ pharmacyId: values.pharmacyId, warning: result.warning });
        return;
      }
      toast.success("제보해 주셔서 감사합니다!");
      goToPharmacyDetail(values.pharmacyId);
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 401) {
          router.push("/login?next=/reports/new");
          return;
        }
        if (error.code === "DUPLICATE_REPORT") {
          form.setError("root", {
            message: "오늘 이미 이 약국의 해당 약품 가격을 제보하셨습니다.",
          });
          return;
        }
        if (error.fieldErrors?.length) {
          for (const fieldError of error.fieldErrors) {
            if ((REPORT_FIELDS as readonly string[]).includes(fieldError.field)) {
              form.setError(fieldError.field as (typeof REPORT_FIELDS)[number], {
                message: fieldError.reason,
              });
            }
          }
          return;
        }
        form.setError("root", { message: error.message });
        return;
      }
      form.setError("root", { message: "제보에 실패했습니다. 다시 시도해 주세요." });
    }
  });

  const { errors, isSubmitting } = form.formState;
  const uploadedFileId = form.watch("uploadedFileId");

  return (
    <div className="mx-auto max-w-lg space-y-6 px-4 py-8">
      <div className="space-y-1">
        <h1 className="text-xl font-bold tracking-tight">가격 제보하기</h1>
        <p className="text-muted-foreground text-sm">30초면 끝나요. 제보해 주시면 다른 사용자에게 바로 도움이 됩니다.</p>
      </div>

      <FormProvider {...form}>
        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
          <PharmacyPicker />
          <DrugPicker />

          <div className="flex flex-col gap-1.5">
            <label htmlFor="price" className="text-sm font-medium">
              가격
            </label>
            <div className="relative">
              <Input
                id="price"
                inputMode="numeric"
                aria-invalid={Boolean(errors.price)}
                aria-describedby={errors.price ? "price-error" : "price-hint"}
                placeholder="0"
                className="pr-8 text-base"
                value={priceText}
                onChange={(event) => {
                  const { numeric, display } = parsePriceInput(event.target.value);
                  setPriceText(display);
                  form.setValue("price", numeric, { shouldValidate: true, shouldDirty: true });
                }}
                onBlur={() => form.trigger("price")}
              />
              <span className="text-muted-foreground pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-sm">
                원
              </span>
            </div>
            <p id="price-hint" className="text-muted-foreground text-xs">
              {MIN_PRICE.toLocaleString("ko-KR")}~{MAX_PRICE.toLocaleString("ko-KR")}원
            </p>
            {errors.price && (
              <p id="price-error" className="text-sm text-destructive">
                {errors.price.message}
              </p>
            )}
          </div>

          {!advancedOpen ? (
            <Button type="button" variant="ghost" size="sm" className="w-fit" onClick={() => setAdvancedOpen(true)}>
              구매일 · 영수증 · 메모 (선택)
            </Button>
          ) : (
            <div className="flex flex-col gap-4 rounded-lg border p-4">
              <div className="flex flex-col gap-1.5">
                <label htmlFor="purchasedAt" className="text-sm font-medium">
                  구매일
                </label>
                <Input
                  id="purchasedAt"
                  type="date"
                  max={todayInKST()}
                  aria-invalid={Boolean(errors.purchasedAt)}
                  aria-describedby={errors.purchasedAt ? "purchasedAt-error" : "purchasedAt-hint"}
                  {...form.register("purchasedAt")}
                />
                <p id="purchasedAt-hint" className="text-muted-foreground text-xs">
                  오늘부터 {MAX_PAST_DAYS}일 이내만 입력할 수 있습니다.
                </p>
                {errors.purchasedAt && (
                  <p id="purchasedAt-error" className="text-sm text-destructive">
                    {errors.purchasedAt.message}
                  </p>
                )}
              </div>

              <div className="flex flex-col gap-1.5">
                <span className="text-sm font-medium">영수증 (선택)</span>
                {uploadedFileId ? (
                  <div className="flex items-center justify-between rounded-lg border px-3 py-2 text-sm">
                    <span>영수증이 첨부되었습니다.</span>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => form.setValue("uploadedFileId", null)}
                    >
                      제거
                    </Button>
                  </div>
                ) : (
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    disabled={uploadMutation.isPending}
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      if (file) uploadMutation.mutate(file);
                    }}
                    className="text-sm"
                  />
                )}
                {uploadMutation.isPending && (
                  <p className="text-muted-foreground text-xs">업로드 중…</p>
                )}
                {uploadMutation.isError && (
                  <p className="text-sm text-destructive">
                    {uploadMutation.error instanceof ApiError
                      ? uploadMutation.error.message
                      : "업로드에 실패했습니다."}
                  </p>
                )}
                <p className="text-muted-foreground text-xs">OCR은 하지 않습니다. 관리자 확인용으로만 쓰입니다.</p>
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor="memo" className="text-sm font-medium">
                  메모 (선택)
                </label>
                <textarea
                  id="memo"
                  maxLength={200}
                  rows={3}
                  placeholder="예: 1+1 행사 아님, 정가"
                  aria-invalid={Boolean(errors.memo)}
                  aria-describedby={errors.memo ? "memo-error" : undefined}
                  className="border-input focus-visible:border-ring focus-visible:ring-ring/50 min-h-16 w-full rounded-lg border bg-transparent px-2.5 py-1.5 text-sm outline-none focus-visible:ring-3"
                  {...form.register("memo")}
                />
                {errors.memo && (
                  <p id="memo-error" className="text-sm text-destructive">
                    {errors.memo.message}
                  </p>
                )}
              </div>
            </div>
          )}

          {errors.root && (
            <p role="alert" className="text-sm text-destructive">
              {errors.root.message}
            </p>
          )}

          <Button type="submit" disabled={isSubmitting || uploadMutation.isPending} className="mt-1">
            {isSubmitting ? "제보하는 중…" : "제보하기"}
          </Button>
        </form>
      </FormProvider>

      <Dialog open={flaggedResult !== null} onOpenChange={(open) => { if (!open) setFlaggedResult(null); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>이 가격, 조금 이상해요</DialogTitle>
            <DialogDescription>{flaggedResult?.warning}</DialogDescription>
          </DialogHeader>
          <p className="text-muted-foreground text-sm">그래도 제보되었습니다. 관리자 확인 후 통계에 반영될 수 있습니다.</p>
          <DialogFooter>
            <Button
              onClick={() => {
                if (!flaggedResult) return;
                const { pharmacyId } = flaggedResult;
                setFlaggedResult(null);
                goToPharmacyDetail(pharmacyId);
              }}
            >
              확인
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
