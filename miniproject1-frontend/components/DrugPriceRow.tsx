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
 * 약국 상세(T-21) 취급 약품 목록의 카드 한 장.
 *
 * 예전엔 5컬럼 표의 행이었지만 375px에서 표가 카드 폭보다 넓어져 가로
 * 스크롤이 필요했다(T-36). 정보 중요도에 따라 세로로 쌓는 카드로 바꿔
 * 약품명·대표가격은 한 줄에 크게 두고, 제보 수·최근 갱신·전국 평균 대비는
 * 보조 정보로 그 아래 한 줄에 흘려보낸다 — 좁은 화면에서도 가로 스크롤이
 * 필요 없다.
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
    <li className="rounded-lg border">
      <button
        type="button"
        aria-expanded={isExpanded}
        onClick={() => setIsExpanded((prev) => !prev)}
        className="hover:bg-muted/30 flex w-full flex-col gap-2 rounded-lg p-4 text-left"
      >
        <div className="flex items-center justify-between gap-3">
          <span className="flex min-w-0 items-center gap-1.5 font-medium">
            <ChevronRight
              aria-hidden="true"
              className={cn(
                "text-muted-foreground size-4 shrink-0 transition-transform",
                isExpanded && "rotate-90",
              )}
            />
            <span className="truncate">
              {displayName}
              {packageUnit ? (
                <span className="text-muted-foreground ml-1 text-xs font-normal">
                  {packageUnit}
                </span>
              ) : null}
            </span>
          </span>
          <PriceTag price={repPrice} size="sm" className="shrink-0" />
        </div>

        <div className="text-muted-foreground flex flex-wrap items-center gap-x-3 gap-y-1 pl-6 text-xs">
          <span>제보 {reportCount != null ? `${reportCount}건` : "-"}</span>
          <span>{lastReportedAt ? formatRelativeDate(lastReportedAt) : "-"} 갱신</span>
          {diffFromNationalAvg != null ? (
            <span
              className={cn(
                diffFromNationalAvg < 0 && "text-price-lowest font-medium",
              )}
            >
              전국 평균 대비 {diffFromNationalAvg < 0 ? "-" : "+"}
              {formatNumber(Math.abs(diffFromNationalAvg))}원
            </span>
          ) : null}
        </div>
      </button>

      {isExpanded ? (
        <div className="bg-muted/20 border-t px-4 pt-3 pb-4">
          <PriceHistoryChart pharmacyId={pharmacyId} drugId={drugId} isExpanded={isExpanded} />
        </div>
      ) : null}
    </li>
  );
}
