"use client";

import { useQuery } from "@tanstack/react-query";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { apiFetch } from "@/lib/api";
import type { components } from "@/types/api";

type DrugPage = components["schemas"]["PageResponseDrugSummaryResponse"];
type DrugSummary = components["schemas"]["DrugSummaryResponse"];

function hasIdAndName(
  item: DrugSummary,
): item is DrugSummary & { id: number; displayName: string } {
  return typeof item.id === "number" && typeof item.displayName === "string";
}

export interface DrugSelectProps {
  value: number | undefined;
  onChange: (drugId: number | undefined) => void;
  placeholder?: string;
  className?: string;
}

/**
 * 약품 전체 목록(현재 36개) 드롭다운.
 *
 * `DrugAutocomplete`(components/DrugAutocomplete.tsx, T-17)는 타이핑
 * 자동완성이라 "검색어 2글자 이상"부터 시작한다 — 목록 전체가 눈에 보여야
 * 하는 관리자 통계 필터에는 맞지 않는다. 대신 `GET /api/v1/drugs`를 q 없이
 * 호출해(DrugController가 q를 optional로 받는다) size=50으로 전체를 한 번에
 * 받아 평범한 Select로 보여준다. RegionPicker.tsx와 같은 이유로
 * staleTime: Infinity — 세션 중 약품 마스터가 바뀌지 않는다.
 */
export function DrugSelect({ value, onChange, placeholder = "약품 선택", className }: DrugSelectProps) {
  const query = useQuery({
    queryKey: ["drugs", "all"],
    queryFn: () => apiFetch<DrugPage>("/api/v1/drugs?size=50"),
    staleTime: Infinity,
  });

  const items = (query.data?.content ?? []).filter(hasIdAndName);

  return (
    <Select
      value={value ? String(value) : ""}
      onValueChange={(next) => onChange(next ? Number(next) : undefined)}
      disabled={query.isLoading}
    >
      <SelectTrigger className={className}>
        <SelectValue placeholder={query.isLoading ? "불러오는 중…" : placeholder} />
      </SelectTrigger>
      <SelectContent>
        {items.map((item) => (
          <SelectItem key={item.id} value={String(item.id)}>
            {item.displayName}
            {item.packageUnit ? ` · ${item.packageUnit}` : ""}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
