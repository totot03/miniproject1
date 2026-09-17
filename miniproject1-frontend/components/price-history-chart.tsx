"use client";

import { useQuery } from "@tanstack/react-query";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type DotItemDotProps,
} from "recharts";

import { ErrorState } from "@/components/common/ErrorState";
import { Skeleton } from "@/components/ui/skeleton";
import { apiFetch } from "@/lib/api";
import { formatNumber } from "@/lib/format";
import type { components } from "@/types/api";

type PriceHistoryResponse = components["schemas"]["PriceHistoryResponse"];
type PricePoint = components["schemas"]["PricePoint"];

export interface PriceHistoryChartProps {
  pharmacyId: number;
  drugId: number;
  /** 펼쳐진 행일 때만 이력을 불러온다 — 접힌 상태에선 쿼리 자체를 실행하지 않는다. */
  isExpanded: boolean;
}

type ChartPoint = { purchasedAt: string; price: number; flagged: boolean; label: string };

/** "2026-06-02" -> "06/02". 백엔드가 항상 LocalDate ISO 형식을 주므로 별도 파싱 없이 슬라이스한다. */
function formatMonthDay(isoDate: string): string {
  return isoDate.slice(5).replace("-", "/");
}

/** openapi-typescript가 필수 여부를 모르면 전부 optional로 만든다 — DrugAutocomplete.tsx의 hasIdAndName과 같은 패턴. */
function hasRequiredFields(
  point: PricePoint,
): point is PricePoint & { purchasedAt: string; price: number; flagged: boolean } {
  return (
    typeof point.purchasedAt === "string" &&
    typeof point.price === "number" &&
    typeof point.flagged === "boolean"
  );
}

/**
 * flagged 포인트는 속이 빈 점선 원, 일반 포인트는 채워진 원으로 그린다.
 * 색상만 다르게 하면 색약 사용자에게 구분이 전달되지 않는다(PRD §8 접근성).
 */
function renderDot(props: DotItemDotProps) {
  const { cx, cy, payload } = props as DotItemDotProps & { payload?: ChartPoint };
  if (typeof cx !== "number" || typeof cy !== "number") return <g />;

  if (payload?.flagged) {
    return (
      <circle
        key={`dot-${payload.purchasedAt}`}
        cx={cx}
        cy={cy}
        r={5}
        fill="var(--color-background)"
        stroke="var(--color-muted-foreground)"
        strokeWidth={1.5}
        strokeDasharray="2 2"
      />
    );
  }

  return (
    <circle
      key={`dot-${payload?.purchasedAt ?? cx}`}
      cx={cx}
      cy={cy}
      r={3.5}
      fill="var(--color-chart-3)"
    />
  );
}

function ChartTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: { payload: ChartPoint }[];
}) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;

  return (
    <div className="bg-popover text-popover-foreground rounded-md border px-2.5 py-1.5 text-xs shadow-md">
      <p className="font-medium">{point.purchasedAt}</p>
      <p>{formatNumber(point.price)}원</p>
      {point.flagged ? (
        <p className="text-muted-foreground mt-0.5">통계에서 제외된 제보</p>
      ) : null}
    </div>
  );
}

/**
 * 약국 상세(T-21) 가격표 행을 펼쳤을 때 보여주는 이력 스파크라인.
 *
 * DrugAutocomplete.tsx와 같은 방식으로 `enabled: isExpanded`를 써서, 접힌 행이
 * 마운트돼 있어도(부모가 애니메이션 등을 위해 유지하는 경우) 쿼리는 펼치기
 * 전까지 실행되지 않는다.
 */
export function PriceHistoryChart({ pharmacyId, drugId, isExpanded }: PriceHistoryChartProps) {
  const query = useQuery({
    queryKey: ["price-history", pharmacyId, drugId],
    queryFn: () =>
      apiFetch<PriceHistoryResponse>(
        `/api/v1/pharmacies/${pharmacyId}/drugs/${drugId}/history`,
      ),
    enabled: isExpanded,
  });

  if (!isExpanded) return null;

  if (query.isLoading) {
    return <Skeleton className="h-40 w-full" />;
  }

  if (query.isError) {
    return (
      <ErrorState
        error={query.error}
        fallbackMessage="가격 이력을 불러오지 못했습니다."
        onRetry={() => query.refetch()}
        className="py-4"
      />
    );
  }

  const points = (query.data?.points ?? []).filter(hasRequiredFields);

  if (points.length === 0) {
    return (
      <p className="text-muted-foreground py-4 text-center text-sm">
        최근 가격 제보 이력이 없습니다.
      </p>
    );
  }

  // 이력이 1건뿐이어도(ROADMAP T-21 7번) category형 XAxis라 도메인 계산에서
  // 터지지 않는다 — 수치형(time) 스케일과 달리 값이 1개여도 유효한 스케일이다.
  const chartData: ChartPoint[] = points.map((point) => ({
    ...point,
    label: formatMonthDay(point.purchasedAt),
  }));

  return (
    <div className="h-40 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
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
            width={56}
            domain={["auto", "auto"]}
          />
          <Tooltip content={<ChartTooltip />} />
          <Line
            type="monotone"
            dataKey="price"
            stroke="var(--color-chart-3)"
            strokeWidth={2}
            dot={renderDot}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
