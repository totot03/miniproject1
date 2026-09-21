"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { MapPin, Search } from "lucide-react";

import { DistanceBadge } from "@/components/common/DistanceBadge";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { NearbyPharmacyMap } from "@/components/location/NearbyPharmacyMap";
import { RegionPicker } from "@/components/location/RegionPicker";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useUserLocation } from "@/hooks/useUserLocation";
import { apiFetch } from "@/lib/api";
import type { components } from "@/types/api";

type PharmacySummary = components["schemas"]["PharmacySummaryResponse"];
type PharmacyPage = components["schemas"]["PageResponsePharmacySummaryResponse"];

/** 홈 화면 미리보기 목록이라 검색 화면(SortToggle 2km 기본값)보다 살짝 넓게, 개수는 적게 잡는다. */
const NEARBY_RADIUS_M = 3000;
const NEARBY_SIZE = 6;

/** openapi-typescript가 필수 여부를 모르면 전부 optional로 만든다 — DrugAutocomplete.tsx hasIdAndName과 같은 패턴. */
function hasIdAndName(
  item: PharmacySummary,
): item is PharmacySummary & { id: number; name: string } {
  return typeof item.id === "number" && typeof item.name === "string";
}

/** 지도 마커는 좌표까지 있어야 하므로 hasIdAndName보다 한 단계 더 방어적으로 거른다. */
function hasMapFields(
  item: PharmacySummary,
): item is PharmacySummary & { id: number; name: string; lat: number; lng: number } {
  return (
    typeof item.id === "number" &&
    typeof item.name === "string" &&
    typeof item.lat === "number" &&
    typeof item.lng === "number"
  );
}

/**
 * 홈 화면의 "현재 위치 → 근처 약국" 섹션.
 *
 * 약품을 먼저 고르지 않아도 위치만으로 둘러볼 수 있는 진입점이다(기존
 * DrugAutocomplete 흐름과 병행). 이미 확정된 위치(Redux)가 있으면 마운트
 * 시 그 좌표로 바로 조회하지만, "현재 위치" 버튼은 항상 GPS를 새로
 * 요청한다 — 캐시된 위치를 그대로 재사용하면 실제로는 오래된 위치인데도
 * "현재 위치"라는 이름과 어긋나기 때문이다. 바로 옆 "위치 검색" 버튼은
 * 시·도/시·군·구를 직접 고르는 RegionPicker를 항상 열 수 있게 한다(GPS
 * 거부/실패 시에도 같은 다이얼로그로 대체 지역을 고른다).
 * 약국 상세로 넘어가되 검색 결과가 아니라 위치 기반 목록(가격 없음)이라
 * PharmacyResultCard 대신 이 컴포넌트 안에 간단한 카드를 직접 둔다.
 */
export function NearbyPharmacies() {
  const { state, requestGpsLocation, selectRegionFallback } = useUserLocation();
  const [pickerOpen, setPickerOpen] = useState(false);
  // 카카오맵 키 누락 또는 SDK 로드 실패 시 지도 영역만 감춘다(리스트는 그대로 남는다) —
  // components/SearchResultsWithMap.tsx의 mapFailed와 같은 패턴.
  const [mapFailed, setMapFailed] = useState(
    !process.env.NEXT_PUBLIC_KAKAO_MAP_KEY,
  );

  const hasLocation = state.status === "granted" || state.status === "fallback";
  const lat = hasLocation ? state.lat : null;
  const lng = hasLocation ? state.lng : null;

  const query = useQuery({
    queryKey: ["nearby-pharmacies", lat, lng],
    queryFn: () => {
      const params = new URLSearchParams({
        lat: String(lat),
        lng: String(lng),
        radius: String(NEARBY_RADIUS_M),
        size: String(NEARBY_SIZE),
      });
      return apiFetch<PharmacyPage>(`/api/v1/pharmacies?${params.toString()}`);
    },
    enabled: lat != null && lng != null,
  });

  // "현재 위치" 버튼은 이름 그대로 매번 실제 GPS 좌표를 새로 물어본다 —
  // 이미 확정된 위치가 있다고 그걸 재사용하면(예: sessionStorage에 남은
  // 예전 세션 위치) 버튼을 눌러도 화면이 실제 현재 위치로 갱신되지 않는다.
  function handleUseCurrentLocation() {
    requestGpsLocation(() => setPickerOpen(true));
  }

  const items = (query.data?.content ?? []).filter(hasIdAndName);
  const mapPharmacies = items.filter(hasMapFields);
  const showDenied = state.status === "denied" || state.status === "unavailable";

  return (
    <section className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-semibold tracking-tight">내 주변 약국</h2>
        <div className="flex gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleUseCurrentLocation}
            disabled={state.status === "requesting"}
          >
            <MapPin className="size-4" />
            {state.status === "requesting" ? "위치 확인 중..." : "현재 위치"}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setPickerOpen(true)}
          >
            <Search className="size-4" />
            위치 검색
          </Button>
        </div>
      </div>

      {lat != null && lng != null && !mapFailed ? (
        <NearbyPharmacyMap
          pharmacies={mapPharmacies}
          userLocation={{ lat, lng }}
          onLoadError={() => setMapFailed(true)}
        />
      ) : null}

      {showDenied ? (
        <EmptyState
          title="위치를 가져올 수 없습니다"
          description="위 위치 검색 버튼으로 지역을 직접 선택해 보세요."
        />
      ) : !hasLocation ? null : query.isLoading ? (
        <LoadingSkeleton count={3} />
      ) : query.isError ? (
        <ErrorState
          error={query.error}
          fallbackMessage="주변 약국을 불러오지 못했습니다."
          onRetry={() => query.refetch()}
        />
      ) : items.length === 0 ? (
        <EmptyState
          title="주변에 등록된 약국이 없습니다"
          description="위 위치 검색 버튼으로 다른 지역을 선택해 보세요."
        />
      ) : (
        <div className="grid gap-2 sm:grid-cols-2">
          {items.map((pharmacy) => (
            <Link key={pharmacy.id} href={`/pharmacies/${pharmacy.id}`} className="block">
              <Card className="hover:bg-muted/30">
                <CardContent className="flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-medium">{pharmacy.name}</p>
                    {pharmacy.addressRoad ? (
                      <p className="text-muted-foreground truncate text-xs">
                        {pharmacy.addressRoad}
                      </p>
                    ) : null}
                  </div>
                  {pharmacy.distanceM != null ? (
                    <DistanceBadge meters={pharmacy.distanceM} className="shrink-0" />
                  ) : null}
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <RegionPicker
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onSelect={selectRegionFallback}
      />
    </section>
  );
}
