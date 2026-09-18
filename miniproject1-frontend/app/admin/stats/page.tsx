"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { AdminNav } from "@/components/admin/AdminNav";
import { DrugSelect } from "@/components/admin/DrugSelect";
import { RequireAdmin } from "@/components/admin/RequireAdmin";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { PriceGapBars, type PriceGapRow } from "@/components/charts/PriceGapBars";
import { PriceHistogramChart, type PriceHistogramBucket } from "@/components/charts/PriceHistogramChart";
import { RegionAverageBarChart, type RegionAverage } from "@/components/charts/RegionAverageBarChart";
import { RegionStatsTable } from "@/components/charts/RegionStatsTable";
import { Card, CardContent, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { apiFetch } from "@/lib/api";

/**
 * docs/API.md §8 GET /admin/stats/drugs/{drugId} 응답.
 * types/api.ts에 없어(app/admin/page.tsx와 같은 사정) 손으로 옮긴다.
 */
interface AdminDrugStatsResponse {
  drug: { id: number; displayName: string; packageUnit: string | null };
  distribution: PriceHistogramBucket[];
  byRegion: RegionAverage[];
  national: { avg: number | null; median: number | null; min: number | null; max: number | null; stdDev: number | null };
}

/** docs/API.md §8 GET /admin/stats/price-gaps 응답. */
interface AdminPriceGapResponse {
  rows: PriceGapRow[];
}

const PRICE_GAP_LIMIT = 10;

function DrugDistributionTab() {
  const [drugId, setDrugId] = useState<number | undefined>(undefined);

  const query = useQuery({
    queryKey: ["admin-drug-stats", drugId],
    queryFn: () => apiFetch<AdminDrugStatsResponse>(`/api/v1/admin/stats/drugs/${drugId}`, { auth: true }),
    enabled: drugId !== undefined,
  });

  return (
    <div className="space-y-4">
      <DrugSelect value={drugId} onChange={setDrugId} placeholder="약품을 선택하세요" className="w-56" />

      {drugId === undefined ? (
        <EmptyState title="약품을 선택하세요" description="선택한 약품의 가격 분포와 지역별 평균을 보여줍니다." />
      ) : null}

      {query.isLoading ? <LoadingSkeleton count={2} /> : null}

      {query.isError ? (
        <ErrorState
          error={query.error}
          fallbackMessage="약품 통계를 불러오지 못했습니다."
          onRetry={() => query.refetch()}
        />
      ) : null}

      {query.data ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card>
            <CardContent className="space-y-3">
              <CardTitle>가격 분포</CardTitle>
              {query.data.distribution.length === 0 ? (
                <p className="text-muted-foreground py-8 text-center text-sm">가격 데이터가 없습니다.</p>
              ) : (
                <PriceHistogramChart buckets={query.data.distribution} />
              )}
            </CardContent>
          </Card>
          <Card>
            <CardContent className="space-y-3">
              <CardTitle>지역별 평균가</CardTitle>
              {query.data.byRegion.length === 0 ? (
                <p className="text-muted-foreground py-8 text-center text-sm">가격 데이터가 없습니다.</p>
              ) : (
                <RegionAverageBarChart regions={query.data.byRegion} />
              )}
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  );
}

function PriceGapTab() {
  const query = useQuery({
    queryKey: ["admin-price-gaps", PRICE_GAP_LIMIT],
    queryFn: () =>
      apiFetch<AdminPriceGapResponse>(`/api/v1/admin/stats/price-gaps?limit=${PRICE_GAP_LIMIT}`, {
        auth: true,
      }),
  });

  if (query.isLoading) return <LoadingSkeleton count={5} />;

  if (query.isError) {
    return (
      <ErrorState
        error={query.error}
        fallbackMessage="가격 격차 통계를 불러오지 못했습니다."
        onRetry={() => query.refetch()}
      />
    );
  }

  const rows = query.data?.rows ?? [];
  if (rows.length === 0) {
    return (
      <EmptyState
        title="표본이 부족합니다"
        description="지역당 3건 미만인 표본은 제외되어, 아직 비교할 만한 데이터가 없습니다."
      />
    );
  }

  return (
    <Card>
      <CardContent>
        <PriceGapBars rows={rows} />
      </CardContent>
    </Card>
  );
}

/** `/admin/stats` 관리자 통계 차트 화면 (docs/ROADMAP.md T-34). */
export default function AdminStatsPage() {
  return (
    <RequireAdmin>
      <div className="mx-auto max-w-5xl space-y-6 px-4 py-8">
        <div className="space-y-3">
          <h1 className="text-xl font-bold tracking-tight">관리자 통계</h1>
          <AdminNav />
        </div>

        <Tabs defaultValue="regions">
          <TabsList>
            <TabsTrigger value="regions">지역별 통계</TabsTrigger>
            <TabsTrigger value="drugs">약품별 분포</TabsTrigger>
            <TabsTrigger value="price-gaps">가격 격차 Top 10</TabsTrigger>
          </TabsList>

          <TabsContent value="regions">
            <RegionStatsTable />
          </TabsContent>
          <TabsContent value="drugs">
            <DrugDistributionTab />
          </TabsContent>
          <TabsContent value="price-gaps">
            <PriceGapTab />
          </TabsContent>
        </Tabs>
      </div>
    </RequireAdmin>
  );
}
