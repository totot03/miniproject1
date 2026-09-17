"use client";

import { useState } from "react";
import { ChevronRight } from "lucide-react";

import { PriceTag } from "@/components/common/PriceTag";
import { PriceHistoryChart } from "@/components/price-history-chart";
import { formatNumber, formatRelativeDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { components } from "@/types/api";

type DrugPriceItem = components["schemas"]["DrugPriceItem"];

export interface DrugPriceRowProps {
  pharmacyId: number;
  drugPrice: DrugPriceItem;
}

type RequiredDrugPriceItem = DrugPriceItem & {
  drugId: number;
  displayName: string;
  repPrice: number;
};

/** openapi-typescript가 필수 여부를 모르면 전부 optional로 만든다 — PharmacyResultCard.tsx의 hasRequiredFields와 같은 패턴. */
function hasRequiredFields(item: DrugPriceItem): item is RequiredDrugPriceItem {
  return (
    typeof item.drugId === "number" &&
    typeof item.displayName === "string" &&
    typeof item.repPrice === "number"
  );
}

/**
 * 약국 상세(T-21) drugPrices 표의 행 하나.
 *
 * 펼치면 PriceHistoryChart를 마운트해 T-20 이력을 불러온다 — 접었다 다시 펴도
 * TanStack Query 캐시(staleTime 60초, app/providers.tsx)가 있어 같은 세션에서는
 * 다시 fetch하지 않는다.
 */
export function DrugPriceRow({ pharmacyId, drugPrice }: DrugPriceRowProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  if (!hasRequiredFields(drugPrice)) return null;

  const {
    drugId,
    displayName,
    packageUnit,
    repPrice,
    reportCount,
    lastReportedAt,
    diffFromNationalAvg,
  } = drugPrice;

  return (
    <>
      <tr className="border-b last:border-0">
        <td className="py-3 pr-3">
          <button
            type="button"
            aria-expanded={isExpanded}
            onClick={() => setIsExpanded((prev) => !prev)}
            className="flex items-center gap-1.5 text-left font-medium hover:underline"
          >
            <ChevronRight
              aria-hidden="true"
              className={cn(
                "text-muted-foreground size-4 shrink-0 transition-transform",
                isExpanded && "rotate-90",
              )}
            />
            <span>
              {displayName}
              {packageUnit ? (
                <span className="text-muted-foreground ml-1 text-xs font-normal">
                  {packageUnit}
                </span>
              ) : null}
            </span>
          </button>
        </td>
        <td className="py-3 text-right">
          <PriceTag price={repPrice} size="sm" />
        </td>
        <td className="text-muted-foreground py-3 text-right">
          {reportCount != null ? `${reportCount}건` : "-"}
        </td>
        <td className="text-muted-foreground py-3 text-right">
          {lastReportedAt ? formatRelativeDate(lastReportedAt) : "-"}
        </td>
        <td className="py-3 text-right">
          {diffFromNationalAvg != null ? (
            <span
              className={
                diffFromNationalAvg < 0
                  ? "text-price-lowest font-medium"
                  : "text-muted-foreground"
              }
            >
              {diffFromNationalAvg < 0 ? "-" : "+"}
              {formatNumber(Math.abs(diffFromNationalAvg))}원
            </span>
          ) : (
            <span className="text-muted-foreground">-</span>
          )}
        </td>
      </tr>
      {isExpanded ? (
        <tr>
          <td colSpan={5} className="bg-muted/20 px-2 pb-4">
            <PriceHistoryChart pharmacyId={pharmacyId} drugId={drugId} isExpanded={isExpanded} />
          </td>
        </tr>
      ) : null}
    </>
  );
}
