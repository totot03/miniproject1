"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { AdminNav } from "@/components/admin/AdminNav";
import { RequireAdmin } from "@/components/admin/RequireAdmin";
import { ErrorState } from "@/components/common/ErrorState";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { Card, CardContent, CardDescription, CardTitle } from "@/components/ui/card";
import { ApiError, apiFetch } from "@/lib/api";
import { formatNumber, formatPercent } from "@/lib/format";

/**
 * docs/API.md §8 `GET /admin/stats/overview` 응답.
 *
 * types/api.ts는 T-32 이전에 생성돼 admin 스키마가 없다. gen:api 재생성은
 * 로컬 백엔드+DB 기동이 필요해 이 프런트 전용 티켓 범위 밖이라, 백엔드
 * AdminStatsOverviewResponse를 그대로 옮겨 손으로 선언한다
 * (app/api/auth/session/route.ts가 T-24에서 쓴 것과 같은 방식).
 */
interface AdminStatsOverviewResponse {
  totals: {
    pharmacyCount: number;
    drugCount: number;
    reportCount: number;
    userCount: number;
    coveredPairCount: number;
  };
  recentTrend: { date: string; reportCount: number }[];
  flaggedReportCount: number;
  coverageRate: number;
}

/** "2026-09-15" -> "09/15". PriceHistoryChart.tsx의 formatMonthDay와 같은 방식. */
function formatMonthDay(isoDate: string): string {
  return isoDate.slice(5).replace("-", "/");
}

function TrendChart({ points }: { points: { date: string; reportCount: number }[] }) {
  if (points.length === 0) {
    return (
      <p className="text-muted-foreground py-8 text-center text-sm">
        최근 7일간 제보가 없습니다.
      </p>
    );
  }

  const chartData = points.map((point) => ({ ...point, label: formatMonthDay(point.date) }));

  return (
    <div className="h-56 w-full">
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
            allowDecimals={false}
            tick={{ fontSize: 11, fill: "var(--color-muted-foreground)" }}
            tickFormatter={(value: number) => formatNumber(value)}
            axisLine={false}
            tickLine={false}
            width={40}
          />
          <Tooltip
            formatter={(value) => [`${formatNumber(Number(value))}건`, "제보"] as [string, string]}
          />
          <Line
            type="monotone"
            dataKey="reportCount"
            stroke="var(--color-chart-3)"
            strokeWidth={2}
            dot={{ r: 3.5, fill: "var(--color-chart-3)" }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function AdminDashboardContent() {
  const router = useRouter();

  const query = useQuery({
    queryKey: ["admin-stats-overview"],
    queryFn: () =>
      apiFetch<AdminStatsOverviewResponse>("/api/v1/admin/stats/overview", { auth: true }),
  });

  // 세션이 만료된 채로(리프레시까지 실패) 진입한 경우를 대비한 방어적
  // 리다이렉트 — my-reports/reports-new와 같은 패턴.
  useEffect(() => {
    if (query.error instanceof ApiError && query.error.status === 401) {
      router.push("/login?next=/admin");
    }
  }, [query.error, router]);

  if (query.isLoading) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-8">
        <LoadingSkeleton count={5} />
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-8">
        <ErrorState
          error={query.error}
          fallbackMessage="관리자 통계를 불러오지 못했습니다."
          onRetry={() => query.refetch()}
        />
      </div>
    );
  }

  const data = query.data;
  if (!data) return null;

  const kpis = [
    { label: "약국 수", value: formatNumber(data.totals.pharmacyCount) },
    { label: "약품 수", value: formatNumber(data.totals.drugCount) },
    { label: "제보 수", value: formatNumber(data.totals.reportCount) },
    { label: "커버리지", value: formatPercent(data.coverageRate) },
    { label: "이상치 제보 수", value: formatNumber(data.flaggedReportCount) },
  ];

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-8">
      <div className="space-y-3">
        <h1 className="text-xl font-bold tracking-tight">관리자 대시보드</h1>
        <AdminNav />
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {kpis.map((kpi) => (
          <Card key={kpi.label}>
            <CardContent className="space-y-1">
              <CardDescription>{kpi.label}</CardDescription>
              <CardTitle className="text-2xl">{kpi.value}</CardTitle>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardContent className="space-y-3">
          <CardTitle>최근 7일 제보 추이</CardTitle>
          <TrendChart points={data.recentTrend} />
        </CardContent>
      </Card>
    </div>
  );
}

/** `/admin` 관리자 대시보드 (docs/ROADMAP.md T-33). */
export default function AdminDashboardPage() {
  return (
    <RequireAdmin>
      <AdminDashboardContent />
    </RequireAdmin>
  );
}
