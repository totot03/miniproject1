import Link from "next/link";

import { EmptyState } from "@/components/common/EmptyState";
import { PharmacyResultCard } from "@/components/PharmacyResultCard";
import { SearchResultsWithMap } from "@/components/SearchResultsWithMap";
import { SortToggle, type SearchRadius, type SearchSort } from "@/components/SortToggle";
import { Button } from "@/components/ui/button";
import { apiFetch } from "@/lib/api";
import { formatDistance, formatNumber } from "@/lib/format";
import type { components } from "@/types/api";

type SearchResponse = components["schemas"]["SearchResponse"];

const RADIUS_VALUES: readonly SearchRadius[] = [500, 1000, 2000, 5000];
const SORT_VALUES: readonly SearchSort[] = ["SCORE", "PRICE", "DISTANCE"];
const DEFAULT_RADIUS: SearchRadius = 2000;
const DEFAULT_SORT: SearchSort = "SCORE";

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** docs/API.md §5 radius 허용값(500/1000/2000/5000) 밖이면 기본값으로 보정한다. */
function parseRadius(value: string | string[] | undefined): SearchRadius {
  const num = Number(first(value));
  return (RADIUS_VALUES as readonly number[]).includes(num)
    ? (num as SearchRadius)
    : DEFAULT_RADIUS;
}

/** docs/API.md §5 sort 허용값(SCORE/PRICE/DISTANCE) 밖이면 기본값으로 보정한다. */
function parseSort(value: string | string[] | undefined): SearchSort {
  const raw = first(value) ?? "";
  return (SORT_VALUES as readonly string[]).includes(raw)
    ? (raw as SearchSort)
    : DEFAULT_SORT;
}

/**
 * 검색 결과 화면 (docs/ROADMAP.md T-18).
 *
 * PRD.md §4.2: 검색 결과는 TanStack Query로 옮기지 않는다. 서버 컴포넌트
 * SSR + URL 쿼리(drugId/lat/lng/regionCode/radius/sort)가 이미 그 역할을
 * 한다 — 정렬·반경을 바꾸면 SortToggle이 URL만 바꾸고, 이 컴포넌트가
 * 새 쿼리로 다시 실행된다.
 *
 * Next.js 16의 searchParams는 Promise다(node_modules/next/dist/docs/
 * 01-app/03-api-reference/03-file-conventions/page.md).
 */
export default async function SearchPage(props: PageProps<"/search">) {
  const params = await props.searchParams;
  const drugId = first(params.drugId);
  const lat = first(params.lat);
  const lng = first(params.lng);
  const regionCode = first(params.regionCode);
  const radius = parseRadius(params.radius);
  const sort = parseSort(params.sort);

  const hasLocation = Boolean((lat && lng) || regionCode);

  if (!drugId || !hasLocation) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-8">
        <EmptyState
          title="검색 조건이 올바르지 않습니다"
          description="홈에서 약품과 위치를 다시 선택해 주세요."
          action={
            <Button asChild size="sm">
              <Link href="/">홈으로</Link>
            </Button>
          }
        />
      </div>
    );
  }

  const query = new URLSearchParams({ drugId, radius: String(radius), sort });
  if (lat && lng) {
    query.set("lat", lat);
    query.set("lng", lng);
  } else if (regionCode) {
    query.set("regionCode", regionCode);
  }

  // 실패 시 apiFetch가 던지는 ApiError는 app/search/error.tsx가 받는다.
  const data = await apiFetch<SearchResponse>(
    `/api/v1/search?${query.toString()}`,
  );

  const results = data.results ?? [];
  const summary = data.summary;
  const suggestion = data.suggestion;

  function withRadius(newRadius: number): string {
    const next = new URLSearchParams(query);
    next.set("radius", String(newRadius));
    return `/search?${next.toString()}`;
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-8">
      <div className="space-y-1">
        <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
          {data.drug?.displayName ?? "검색 결과"}
        </h1>
        {summary ? (
          <p className="text-muted-foreground text-sm">
            반경 {formatDistance(radius)} 내 {summary.resultCount ?? 0}곳
            {summary.maxSaving != null && summary.maxSaving > 0
              ? ` · 최대 ${formatNumber(summary.maxSaving)}원 절약 가능`
              : ""}
          </p>
        ) : null}
      </div>

      {data.dataSource === "SEED" || data.dataSource === "MIXED" ? (
        <p className="bg-notice border-notice-border rounded-lg border px-3 py-2 text-xs leading-relaxed">
          이번 검색 결과에는 학습용 예시 데이터가 포함되어 있습니다.
        </p>
      ) : null}

      <SortToggle sort={sort} radius={radius} />

      {results.length === 0 ? (
        <EmptyState
          title="반경 내 검색 결과가 없습니다"
          description={
            suggestion?.recommendedRadius
              ? `반경을 ${formatDistance(suggestion.recommendedRadius)}로 넓히면 ${suggestion.estimatedCount ?? 0}곳이 있습니다.`
              : "다른 약품이나 더 넓은 반경으로 다시 검색해 보세요."
          }
          action={
            suggestion?.recommendedRadius ? (
              <Button asChild size="sm">
                <Link href={withRadius(suggestion.recommendedRadius)}>
                  반경 넓혀서 다시 검색
                </Link>
              </Button>
            ) : undefined
          }
        />
      ) : (
        <SearchResultsWithMap
          results={results}
          userLocation={
            data.query?.lat != null && data.query?.lng != null
              ? { lat: data.query.lat, lng: data.query.lng }
              : undefined
          }
        >
          {results.map((item, index) => (
            <PharmacyResultCard key={item.rank ?? index} item={item} />
          ))}
        </SearchResultsWithMap>
      )}
    </div>
  );
}
