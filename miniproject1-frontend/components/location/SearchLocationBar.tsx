"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { MapPin, RotateCcw } from "lucide-react";

import { RegionPicker } from "@/components/location/RegionPicker";
import { Button } from "@/components/ui/button";
import { useUserLocation } from "@/hooks/useUserLocation";
import { useAppSelector } from "@/lib/hooks";

function labelFor(
  status: ReturnType<typeof useUserLocation>["state"]["status"],
  storedLabel: string | null,
): string {
  switch (status) {
    case "granted":
      return storedLabel ?? "현재 위치 근처";
    case "fallback":
      return storedLabel ?? "선택한 지역";
    case "requesting":
      return "위치 확인 중...";
    case "denied":
      return "위치 접근 거부됨";
    case "unavailable":
      return "위치를 가져올 수 없음";
    case "idle":
    default:
      return "위치가 설정되지 않음";
  }
}

/**
 * 약 검색 화면(/search) 전용 위치 설정 바.
 *
 * 예전에는 헤더의 LocationIndicator가 모든 화면에서 위치를 관리했지만,
 * 이제 "현재 위치 → 근처 약국"은 홈(NearbyPharmacies)이 전담하고, 위치를
 * 수동으로 바꾸는 기능은 약을 검색하는 이 화면에만 둔다. 위치가 바뀌면
 * (GPS 재요청 성공 또는 RegionPicker 선택) 현재 쿼리(drugId/radius/sort)를
 * 그대로 보존한 채 lat/lng만 갱신해 SearchPage(서버 컴포넌트)를 다시
 * 요청시킨다 — SortToggle과 동일한 "URL이 정본" 패턴이다.
 */
export function SearchLocationBar() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { state, requestGpsLocation, selectRegionFallback } = useUserLocation();
  const label = useAppSelector((s) => s.location.label);
  const [pickerOpen, setPickerOpen] = useState(false);
  // requestGpsLocation의 성공 콜백은 Redux에 dispatch만 하고 좌표를 돌려주지
  // 않으므로(useUserLocation.ts), DrugAutocomplete.tsx와 같은 방식으로
  // "GPS 응답을 기다리는 중"을 ref에 표시해두고 아래 effect에서 navigate한다.
  const pendingNavRef = useRef(false);

  function navigateWith(lat: number, lng: number) {
    const params = new URLSearchParams(searchParams);
    params.set("lat", String(lat));
    params.set("lng", String(lng));
    params.delete("regionCode");
    router.push(`${pathname}?${params.toString()}`);
  }

  useEffect(() => {
    if (!pendingNavRef.current) return;
    if (state.status === "granted" || state.status === "fallback") {
      navigateWith(state.lat, state.lng);
      pendingNavRef.current = false;
    }
    // navigateWith는 router/pathname/searchParams에서 파생된 함수라 의존성에서 제외해도 무방하다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state]);

  function handleUseCurrentLocation() {
    pendingNavRef.current = true;
    requestGpsLocation(() => setPickerOpen(true));
  }

  const showResetIcon = state.status === "granted" || state.status === "fallback";

  return (
    <div className="flex flex-wrap items-center gap-2 text-sm">
      <span className="text-muted-foreground flex items-center gap-1">
        <MapPin aria-hidden="true" className="size-4 shrink-0" />
        {labelFor(state.status, label)}
        {showResetIcon ? (
          <RotateCcw aria-hidden="true" className="size-3 shrink-0" />
        ) : null}
      </span>

      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={handleUseCurrentLocation}
        disabled={state.status === "requesting"}
      >
        현재 위치 사용
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="sm"
        onClick={() => setPickerOpen(true)}
      >
        지역 선택
      </Button>

      <RegionPicker
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onSelect={(region) => {
          selectRegionFallback(region);
          navigateWith(region.lat, region.lng);
        }}
      />
    </div>
  );
}
