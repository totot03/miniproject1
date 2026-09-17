"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

export type SearchSort = "SCORE" | "PRICE" | "DISTANCE";
export type SearchRadius = 500 | 1000 | 2000 | 5000;

export interface SortToggleProps {
  sort: SearchSort;
  radius: SearchRadius;
  className?: string;
}

/** docs/API.md §5 sort 파라미터 값과 한글 라벨 */
const SORT_OPTIONS: { value: SearchSort; label: string }[] = [
  { value: "SCORE", label: "추천순" },
  { value: "PRICE", label: "가격순" },
  { value: "DISTANCE", label: "거리순" },
];

/** docs/API.md §5 radius 허용값(500/1000/2000/5000)과 표시 라벨 */
const RADIUS_OPTIONS: { value: SearchRadius; label: string }[] = [
  { value: 500, label: "500m" },
  { value: 1000, label: "1km" },
  { value: 2000, label: "2km" },
  { value: 5000, label: "5km" },
];

/**
 * 정렬·반경 토글 (docs/ROADMAP.md T-18 5번).
 *
 * 정렬·반경은 URL에 남아야 하는 상태다(docs/PRD.md §4.2) — 값이 바뀌면
 * router.push로 app/search/page.tsx(서버 컴포넌트)를 다시 요청시킨다.
 * TanStack Query나 useState로 결과를 다시 fetch하면 검색 결과 상태가
 * URL과 클라이언트 두 곳에 생기므로 절대 하지 않는다.
 *
 * drugId/lat/lng/regionCode 등 나머지 쿼리는 그대로 보존해야 하므로
 * 현재 쿼리 문자열을 복사해 바뀐 키만 덮어쓴다(Next.js 공식 문서의
 * useSearchParams "Updating searchParams" 예제와 동일한 패턴).
 */
export function SortToggle({ sort, radius, className }: SortToggleProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  function updateQuery(key: "sort" | "radius", value: string) {
    const params = new URLSearchParams(searchParams);
    params.set(key, value);
    router.push(`${pathname}?${params.toString()}`);
  }

  return (
    <div className={cn("flex flex-wrap items-center gap-3", className)}>
      <Select
        value={sort}
        onValueChange={(value) => updateQuery("sort", value)}
      >
        <SelectTrigger aria-label="정렬 기준" className="w-28">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {SORT_OPTIONS.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <div className="flex flex-wrap gap-1" role="group" aria-label="검색 반경">
        {RADIUS_OPTIONS.map((option) => (
          <Button
            key={option.value}
            type="button"
            size="sm"
            variant={option.value === radius ? "default" : "outline"}
            aria-pressed={option.value === radius}
            onClick={() => updateQuery("radius", String(option.value))}
          >
            {option.label}
          </Button>
        ))}
      </div>
    </div>
  );
}
