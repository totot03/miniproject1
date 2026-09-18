"use client";

import { formatPrice } from "@/lib/format";
import { cn } from "@/lib/utils";

export interface PriceGapRow {
  drug: { id: number; displayName: string };
  cheapestRegion: { sido: string; sigungu: string; avgPrice: number };
  priciestRegion: { sido: string; sigungu: string; avgPrice: number };
  gap: number;
  gapPct: number;
}

/**
 * 가격 격차 Top 10 (docs/ROADMAP.md T-34 4번).
 *
 * "숫자만으로는 의미가 전달되지 않는다 — 최저가·최고가 지역 이름을 함께
 * 보여준다"는 요구사항 때문에 Recharts 대신 순수 HTML 가로 막대로 만들었다.
 * SVG 안에서 두 지역명 + 가격 + %를 한 줄로 우겨넣으면 라벨이 잘리거나
 * 겹치기 쉬운데(dataviz 스킬 marks-and-anatomy.md), flex + 자연스러운
 * 줄바꿈을 쓰면 375px에서도 텍스트만 wrap되고 잘리지 않는다.
 *
 * 1위(가장 큰 격차)만 강조색(--color-primary, 이 무채색 디자인 시스템에서
 * 가장 어두운 톤)을 쓰고 나머지는 --color-chart-3 — "시리즈당 1색 + 강조색
 * 1개만"(스펙 5번)을 emphasis 형태로 구현한 것.
 */
export function PriceGapBars({ rows }: { rows: PriceGapRow[] }) {
  const maxGapPct = Math.max(...rows.map((row) => row.gapPct), 1);

  return (
    <div className="space-y-4">
      {rows.map((row, index) => {
        const isTop = index === 0;
        const widthPct = Math.max((row.gapPct / maxGapPct) * 100, 2);
        return (
          <div key={row.drug.id}>
            <div className="flex items-baseline justify-between gap-2 text-sm">
              <span className="font-medium">{row.drug.displayName}</span>
              <span className={cn("font-semibold tabular-nums", isTop && "text-base")}>
                {row.gapPct.toFixed(1)}%
              </span>
            </div>
            <div className="bg-chart-1 mt-1.5 h-3 w-full overflow-hidden rounded-full">
              <div
                className={cn("h-full rounded-full", isTop ? "bg-primary" : "bg-chart-3")}
                style={{ width: `${widthPct}%` }}
              />
            </div>
            <p className="text-muted-foreground mt-1 text-xs">
              최저가 {row.cheapestRegion.sido} {row.cheapestRegion.sigungu}{" "}
              {formatPrice(row.cheapestRegion.avgPrice)} → 최고가 {row.priciestRegion.sido}{" "}
              {row.priciestRegion.sigungu} {formatPrice(row.priciestRegion.avgPrice)}
            </p>
          </div>
        );
      })}
    </div>
  );
}
