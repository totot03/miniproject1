"use client";

import Script from "next/script";
import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { EmptyState } from "@/components/common/EmptyState";
import { ErrorState } from "@/components/common/ErrorState";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { apiFetch } from "@/lib/api";
import { useAppSelector } from "@/lib/hooks";
import type { components } from "@/types/api";

type PharmacySummary = components["schemas"]["PharmacySummaryResponse"];
type PharmacyPage = components["schemas"]["PageResponsePharmacySummaryResponse"];

export interface PharmacyMapPickerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (pharmacy: { id: number; name: string }) => void;
}

/** components/pharmacy-map.tsx DEFAULT_CENTER과 같은 값(서울시청) — 확정된 위치가 없을 때만 쓴다. */
const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 };
const RADIUS_M = 3000;
const MAX_RESULTS = 50;

function hasCoords(
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
 * "지도에서 고르기" 모달 (docs/ROADMAP.md T-29 3번).
 *
 * components/pharmacy-map.tsx(T-22)는 검색 결과의 가격 오버레이·최저가 강조에
 * 특화돼 있어 그대로 재사용하지 않는다 — 여기서는 "마커 클릭 = 선택"만 하는
 * 훨씬 가벼운 지도가 필요해서, 같은 카카오 SDK 로딩 패턴만 가져와 새로 만든다.
 * 이 다이얼로그 때문에 새로 GPS 권한을 요청하지 않는다 — 이미 확정된 위치
 * (locationSlice)가 있으면 그 좌표를, 없으면 기본 중심으로 주변 약국을 보여준다.
 */
export function PharmacyMapPickerDialog({
  open,
  onOpenChange,
  onSelect,
}: PharmacyMapPickerDialogProps) {
  const location = useAppSelector((state) => state.location);
  const center =
    location.lat != null && location.lng != null
      ? { lat: location.lat, lng: location.lng }
      : DEFAULT_CENTER;

  const appKey = process.env.NEXT_PUBLIC_KAKAO_MAP_KEY;
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<kakao.maps.Map | null>(null);
  const markersRef = useRef<kakao.maps.Marker[]>([]);
  const overlaysRef = useRef<kakao.maps.CustomOverlay[]>([]);
  const onSelectRef = useRef(onSelect);
  const [sdkReady, setSdkReady] = useState(false);

  useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  const query = useQuery({
    queryKey: ["pharmacies", "map-picker", center.lat, center.lng],
    queryFn: () =>
      apiFetch<PharmacyPage>(
        `/api/v1/pharmacies?lat=${center.lat}&lng=${center.lng}&radius=${RADIUS_M}&size=${MAX_RESULTS}`,
      ),
    enabled: open,
  });

  const pharmacies = (query.data?.content ?? []).filter(hasCoords);
  const pharmacyKey = pharmacies.map((p) => `${p.id}:${p.lat}:${p.lng}`).join("|");

  // 다이얼로그가 열려 있고 SDK가 준비됐을 때만 지도를 만든다. 닫히면 Radix가
  // DialogContent를 언마운트하므로 cleanup에서 mapRef를 비워 다음에 열릴 때
  // 새로 만들어지게 한다(components/pharmacy-map.tsx와 같은 이유).
  useEffect(() => {
    if (!open || !sdkReady || !containerRef.current || mapRef.current) return;
    mapRef.current = new window.kakao.maps.Map(containerRef.current, {
      center: new window.kakao.maps.LatLng(center.lat, center.lng),
      level: 5,
    });
    return () => {
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, sdkReady]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    markersRef.current.forEach((marker) => marker.setMap(null));
    markersRef.current = [];
    overlaysRef.current.forEach((overlay) => overlay.setMap(null));
    overlaysRef.current = [];

    const bounds = new window.kakao.maps.LatLngBounds();
    for (const pharmacy of pharmacies) {
      const position = new window.kakao.maps.LatLng(pharmacy.lat, pharmacy.lng);
      bounds.extend(position);

      const marker = new window.kakao.maps.Marker({ map, position });
      window.kakao.maps.event.addListener(marker, "click", () =>
        onSelectRef.current({ id: pharmacy.id, name: pharmacy.name }),
      );
      markersRef.current.push(marker);

      // types/kakao-maps.d.ts에는 Marker에 title(네이티브 툴팁) 옵션이 없어, 이름표는
      // components/pharmacy-map.tsx의 가격 라벨과 같은 CustomOverlay 방식으로 대신한다.
      const label = document.createElement("div");
      label.textContent = pharmacy.name;
      label.style.cssText =
        "padding:2px 6px;border-radius:6px;font-size:11px;font-weight:600;white-space:nowrap;" +
        "transform:translateY(-6px);box-shadow:0 1px 2px rgba(0,0,0,0.18);" +
        "border:1px solid var(--border,#e5e5e5);background:var(--card,#fff);color:var(--card-foreground,#171717);";
      const overlay = new window.kakao.maps.CustomOverlay({
        map,
        position,
        content: label,
        yAnchor: 2.4,
      });
      overlaysRef.current.push(overlay);
    }

    if (pharmacies.length > 0) {
      map.relayout();
      map.setBounds(bounds);
    }

    return () => {
      markersRef.current.forEach((marker) => marker.setMap(null));
      markersRef.current = [];
      overlaysRef.current.forEach((overlay) => overlay.setMap(null));
      overlaysRef.current = [];
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pharmacyKey, sdkReady]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>지도에서 약국 고르기</DialogTitle>
          <DialogDescription>마커를 클릭하면 그 약국이 선택됩니다.</DialogDescription>
        </DialogHeader>

        {!appKey ? (
          <ErrorState
            fallbackMessage="지도를 불러올 수 없습니다. 위 검색창에서 약국 이름으로 찾아주세요."
          />
        ) : query.isError ? (
          <ErrorState
            error={query.error}
            fallbackMessage="주변 약국을 불러오지 못했습니다."
            onRetry={() => query.refetch()}
          />
        ) : (
          <div className="relative h-96 w-full overflow-hidden rounded-lg border">
            <Script
              src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`}
              strategy="afterInteractive"
              onLoad={() => window.kakao.maps.load(() => setSdkReady(true))}
            />
            <div ref={containerRef} className="h-full w-full" />
            {!sdkReady ? (
              <div className="bg-muted/30 text-muted-foreground absolute inset-0 flex items-center justify-center text-xs">
                지도를 불러오는 중…
              </div>
            ) : pharmacies.length === 0 && !query.isLoading ? (
              <div className="absolute inset-x-0 bottom-2 flex justify-center">
                <EmptyState
                  title="주변에 등록된 약국이 없습니다"
                  className="border-none bg-transparent py-2"
                />
              </div>
            ) : null}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
