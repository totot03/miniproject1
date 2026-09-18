"use client";

import { Bar, BarChart, CartesianGrid, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { formatNumber } from "@/lib/format";

export interface RegionAverage {
  sido: string;
  sigungu: string;
  avgPrice: number | null;
  pharmacyCount: number;
}

/**
 * 약품별 지역 평균가 막대 차트 (docs/ROADMAP.md T-34 3번).
 *
 * [[seed-data-single-region-pharmacy]] — 시드 약국이 강남구 한 곳에 몰려 있어
 * 막대가 1개뿐일 수 있다. 데이터가 실제로 그렇다는 뜻이지 이 컴포넌트의
 * 결함이 아니다.
 */
export function RegionAverageBarChart({ regions }: { regions: RegionAverage[] }) {
  const data = regions
    .filter((region) => region.avgPrice != null)
    .map((region) => ({ ...region, label: `${region.sido} ${region.sigungu}` }));

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
            tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
            tickFormatter={(value: number) => formatNumber(value)}
            axisLine={false}
            tickLine={false}
            width={48}
          />
          <Tooltip
            formatter={(value, name, item) => [
              `${formatNumber(Number(value))}원 (약국 ${formatNumber(Number((item.payload as RegionAverage).pharmacyCount))}곳)`,
              "평균가",
            ]}
          />
          <Bar dataKey="avgPrice" fill="var(--color-chart-3)" radius={[4, 4, 0, 0]} maxBarSize={40}>
            <LabelList
              dataKey="avgPrice"
              position="top"
              formatter={(value) => `${formatNumber(Number(value))}원`}
              style={{ fill: "var(--color-muted-foreground)", fontSize: 11 }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
