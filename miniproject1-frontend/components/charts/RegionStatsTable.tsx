"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";

import { DrugSelect } from "@/components/admin/DrugSelect";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { apiFetch } from "@/lib/api";
import { formatNumber } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { components } from "@/types/api";

type RegionGroup = components["schemas"]["RegionGroupResponse"];
type SigunguItem = components["schemas"]["SigunguItem"];

/**
 * docs/API.md §8 GET /admin/stats/regions 응답.
 * types/api.ts에는 아직 없다(app/admin/page.tsx와 같은 사정) — 손으로 옮긴다.
 */
interface AdminRegionStatsResponse {
  rows: {
    region: { code: string; sido: string; sigungu: string };
    drug: { id: number; displayName: string };
    avgPrice: number | null;
    minPrice: number | null;
    maxPrice: number | null;
    pharmacyCount: number;
    reportCount: number;
  }[];
}

type Row = AdminRegionStatsResponse["rows"][number];
type SortKey = "region" | "drug" | "avgPrice" | "minPrice" | "maxPrice" | "pharmacyCount" | "reportCount";

const COLUMNS: { key: SortKey; label: string; align: "left" | "right" }[] = [
  { key: "region", label: "지역", align: "left" },
  { key: "drug", label: "약품", align: "left" },
  { key: "avgPrice", label: "평균가", align: "right" },
  { key: "minPrice", label: "최저가", align: "right" },
  { key: "maxPrice", label: "최고가", align: "right" },
  { key: "pharmacyCount", label: "약국 수", align: "right" },
  { key: "reportCount", label: "제보 수", align: "right" },
];

function sortValue(row: Row, key: SortKey): string | number {
  switch (key) {
    case "region":
      return `${row.region.sido} ${row.region.sigungu}`;
    case "drug":
      return row.drug.displayName;
    default:
      return row[key] ?? -1;
  }
}

function hasSido(group: RegionGroup): group is RegionGroup & { sido: string } {
  return typeof group.sido === "string" && group.sido.length > 0;
}

function hasCode(item: SigunguItem): item is SigunguItem & { code: string } {
  return typeof item.code === "string" && item.code.length > 0;
}

/**
 * 지역별 통계 탭 (docs/ROADMAP.md T-34 2번).
 *
 * 시도·시군구 캐스케이드는 RegionPicker.tsx(T-16)와 같은 GET /api/v1/regions
 * 응답 구조를 쓰지만, 여기서는 GPS 폴백용 Dialog가 아니라 탭 안의 평범한
 * 인라인 필터라 Dialog 없이 다시 구현한다 — 재사용처가 이번이 두 번째뿐이라
 * 공용 훅으로 추출하지는 않는다.
 */
export function RegionStatsTable() {
  const [sido, setSido] = useState<string | undefined>(undefined);
  const [sigunguCode, setSigunguCode] = useState<string | undefined>(undefined);
  const [drugId, setDrugId] = useState<number | undefined>(undefined);
  const [sortKey, setSortKey] = useState<SortKey>("reportCount");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");

  const regionsQuery = useQuery({
    queryKey: ["regions"],
    queryFn: () => apiFetch<RegionGroup[]>("/api/v1/regions"),
    staleTime: Infinity,
  });

  const sidoGroups = (regionsQuery.data ?? []).filter(hasSido);
  const sigungus = (
    sidoGroups.find((group) => group.sido === sido)?.sigungus ?? []
  ).filter(hasCode);

  const statsQuery = useQuery({
    queryKey: ["admin-region-stats", sigunguCode ?? sido ?? null, drugId ?? null],
    queryFn: () => {
      const params = new URLSearchParams();
      if (sigunguCode) params.set("regionCode", sigunguCode);
      else if (sido) params.set("sido", sido);
      if (drugId) params.set("drugId", String(drugId));
      const qs = params.toString();
      return apiFetch<AdminRegionStatsResponse>(
        `/api/v1/admin/stats/regions${qs ? `?${qs}` : ""}`,
        { auth: true },
      );
    },
  });

  const rows = useMemo(() => {
    const list = statsQuery.data?.rows ?? [];
    const sorted = [...list].sort((a, b) => {
      const va = sortValue(a, sortKey);
      const vb = sortValue(b, sortKey);
      if (typeof va === "string" || typeof vb === "string") {
        return String(va).localeCompare(String(vb), "ko");
      }
      return va - vb;
    });
    return sortDir === "asc" ? sorted : sorted.reverse();
  }, [statsQuery.data, sortKey, sortDir]);

  function toggleSort(key: SortKey) {
    if (key === sortKey) {
      setSortDir((dir) => (dir === "asc" ? "desc" : "asc"));
    } else {
      setSortKey(key);
      setSortDir("desc");
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={sido ?? ""}
          onValueChange={(value) => {
            setSido(value || undefined);
            setSigunguCode(undefined);
          }}
        >
          <SelectTrigger className="w-32">
            <SelectValue placeholder="시·도 전체" />
          </SelectTrigger>
          <SelectContent>
            {sidoGroups.map((group) => (
              <SelectItem key={group.sido} value={group.sido}>
                {group.sido}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select
          value={sigunguCode ?? ""}
          onValueChange={(value) => setSigunguCode(value || undefined)}
          disabled={!sido}
        >
          <SelectTrigger className="w-36">
            <SelectValue placeholder="시·군·구 전체" />
          </SelectTrigger>
          <SelectContent>
            {sigungus.map((item) => (
              <SelectItem key={item.code} value={item.code}>
                {item.sigungu}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <DrugSelect value={drugId} onChange={setDrugId} placeholder="약품 전체" className="w-40" />
      </div>

      {statsQuery.isLoading ? <LoadingSkeleton count={4} /> : null}

      {statsQuery.isError ? (
        <ErrorState
          error={statsQuery.error}
          fallbackMessage="지역별 통계를 불러오지 못했습니다."
          onRetry={() => statsQuery.refetch()}
        />
      ) : null}

      {statsQuery.isSuccess && rows.length === 0 ? (
        <EmptyState title="조건에 맞는 데이터가 없습니다" description="필터를 바꿔서 다시 확인해 보세요." />
      ) : null}

      {rows.length > 0 ? (
        // 스펙 6번 — 테이블만 가로 스크롤되게 하고, 페이지 자체는 스크롤되지 않아야 한다.
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] border-collapse text-sm">
            <thead>
              <tr className="text-muted-foreground border-b text-xs">
                {COLUMNS.map((column) => (
                  <th
                    key={column.key}
                    className={cn(
                      "pb-2 font-medium",
                      column.align === "right" ? "text-right" : "text-left",
                    )}
                  >
                    <button
                      type="button"
                      onClick={() => toggleSort(column.key)}
                      className={cn(
                        "hover:text-foreground inline-flex items-center gap-1",
                        column.align === "right" && "flex-row-reverse",
                      )}
                    >
                      {column.label}
                      {sortKey === column.key ? (
                        sortDir === "asc" ? (
                          <ArrowUp className="size-3" aria-hidden="true" />
                        ) : (
                          <ArrowDown className="size-3" aria-hidden="true" />
                        )
                      ) : (
                        <ArrowUpDown className="size-3 opacity-40" aria-hidden="true" />
                      )}
                    </button>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={`${row.region.code}-${row.drug.id}`} className="border-b last:border-0">
                  <td className="py-2">
                    {row.region.sido} {row.region.sigungu}
                  </td>
                  <td className="py-2">{row.drug.displayName}</td>
                  <td className="py-2 text-right">
                    {row.avgPrice != null ? `${formatNumber(row.avgPrice)}원` : "-"}
                  </td>
                  <td className="py-2 text-right">
                    {row.minPrice != null ? `${formatNumber(row.minPrice)}원` : "-"}
                  </td>
                  <td className="py-2 text-right">
                    {row.maxPrice != null ? `${formatNumber(row.maxPrice)}원` : "-"}
                  </td>
                  <td className="py-2 text-right">{formatNumber(row.pharmacyCount)}</td>
                  <td className="py-2 text-right">{formatNumber(row.reportCount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
  );
}
