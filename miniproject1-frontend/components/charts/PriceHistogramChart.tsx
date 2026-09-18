"use client";

import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { formatNumber } from "@/lib/format";

export interface PriceHistogramBucket {
  bucketFrom: number;
  bucketTo: number;
  count: number;
}

/** "2000~2500" -> "2,000~2,500" */
function formatBucketLabel(bucket: PriceHistogramBucket): string {
  return `${formatNumber(bucket.bucketFrom)}~${formatNumber(bucket.bucketTo)}`;
}

/**
 * 약품별 가격 분포 히스토그램 (docs/ROADMAP.md T-34 3번).
 *
 * 구간이 보통 5~8개뿐이라(dataviz 스킬 기준 "표본이 적으면 매 막대 라벨링이
 * 과하지 않다") 막대마다 건수 라벨을 병기한다. 단일 계열이라 legend는 두지
 * 않는다 — 카드 제목이 이미 무엇을 그리는지 말해준다.
 */
export function PriceHistogramChart({ buckets }: { buckets: PriceHistogramBucket[] }) {
  const data = buckets.map((bucket) => ({ ...bucket, label: formatBucketLabel(bucket) }));

  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 20, right: 12, bottom: 0, left: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
          <XAxis
            dataKey="label"
            tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            allowDecimals={false}
            tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
            axisLine={false}
            tickLine={false}
            width={32}
          />
          <Tooltip formatter={(value) => [`${formatNumber(Number(value))}건`, "건수"]} />
          <Bar dataKey="count" fill="var(--color-chart-3)" radius={[4, 4, 0, 0]} maxBarSize={40}>
            <LabelList
              dataKey="count"
              position="top"
              formatter={(value) => formatNumber(Number(value))}
              style={{ fill: "var(--color-muted-foreground)", fontSize: 11 }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
