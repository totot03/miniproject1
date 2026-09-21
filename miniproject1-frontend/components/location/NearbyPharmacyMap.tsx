"use client";

import Script from "next/script";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

import { MapSkeleton } from "@/components/common/MapSkeleton";

export interface NearbyMapPharmacy {
  id: number;
  name: string;
  lat: number;
  lng: number;
}

export interface NearbyPharmacyMapProps {
  pharmacies: NearbyMapPharmacy[];
  userLocation: { lat: number; lng: number };
  /** 키 누락 또는 SDK 로드 실패 시 1회 호출된다 — 상위(NearbyPharmacies)가 지도 영역을 감춘다. */
  onLoadError: () => void;
}

/** components/pharmacy-map.tsx와 같은 지도 관용색 — 가격 의미 색 토큰과 무관하다. */
const USER_LOCATION_COLOR = "#2563eb";

/** 약국 마커(기본 핀)와 구분되도록 원 모양으로 그린다 — components/pharmacy-map.tsx userLocationImage와 동일. */
function userLocationImage(): kakao.maps.MarkerImage {
  const size = 18;
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">` +
    `<circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 2}" fill="${USER_LOCATION_COLOR}" stroke="white" stroke-width="3"/></svg>`;
  const src = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
  return new window.kakao.maps.MarkerImage(src, new window.kakao.maps.Size(size, size));
}

/**
 * 홈 화면 "내 주변 약국" 지도.
 *
 * components/pharmacy-map.tsx는 검색 결과의 가격 오버레이·최저가 강조에 특화돼
 * 있어 그대로 재사용하지 않는다(components/location/PharmacyMapPickerDialog.tsx와
 * 같은 이유) — 여기서는 이름표 마커 + 현재 위치 마커만 있으면 되므로, 같은 카카오
 * SDK 로딩 패턴만 가져와 가볍게 새로 만든다. 마커 클릭은 약국 상세 페이지로
 * 이동한다(그 페이지가 이미 실제 운영시간을 렌더링한다).
 */
export function NearbyPharmacyMap({
  pharmacies,
  userLocation,
  onLoadError,
}: NearbyPharmacyMapProps) {
  const router = useRouter();
  const appKey = process.env.NEXT_PUBLIC_KAKAO_MAP_KEY;

  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<kakao.maps.Map | null>(null);
  const markersRef = useRef<kakao.maps.Marker[]>([]);
  const overlaysRef = useRef<kakao.maps.CustomOverlay[]>([]);
  const userMarkerRef = useRef<kakao.maps.Marker | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!appKey) onLoadError();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // SDK 스크립트 자체는 로드됐지만(예: 카카오 개발자 콘솔에 등록되지 않은
  // 도메인) kakao.maps.load 콜백이 끝내 안 불려 ready가 영영 false로 남는
  // 경우가 있다 — 이때 Script의 onError는 안 불리므로(네트워크 요청 자체는
  // 성공) 스켈레톤이 무한 로딩으로 남는다. 일정 시간 안에 준비되지 않으면
  // 같은 실패 경로로 보내 상위가 명확한 실패 상태를 보여줄 수 있게 한다.
  useEffect(() => {
    if (!appKey || ready) return;
    const timer = setTimeout(onLoadError, 8000);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, appKey]);

  // React(Strict Mode) 개발 모드의 mount→cleanup→mount 재시뮬레이션 대응 —
  // components/pharmacy-map.tsx와 같은 이유로 cleanup에서 mapRef를 비운다.
  useEffect(() => {
    if (!ready || !containerRef.current || mapRef.current) return;
    mapRef.current = new window.kakao.maps.Map(containerRef.current, {
      center: new window.kakao.maps.LatLng(userLocation.lat, userLocation.lng),
      level: 5,
    });
    return () => {
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready]);

  const pharmacyKey = pharmacies.map((p) => `${p.id}:${p.lat}:${p.lng}`).join("|");
  const userLocationKey = `${userLocation.lat}:${userLocation.lng}`;

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const bounds = new window.kakao.maps.LatLngBounds();

    const userPosition = new window.kakao.maps.LatLng(userLocation.lat, userLocation.lng);
    userMarkerRef.current = new window.kakao.maps.Marker({
      map,
      position: userPosition,
      image: userLocationImage(),
      zIndex: 10,
    });
    bounds.extend(userPosition);

    for (const pharmacy of pharmacies) {
      const position = new window.kakao.maps.LatLng(pharmacy.lat, pharmacy.lng);
      bounds.extend(position);

      const marker = new window.kakao.maps.Marker({ map, position });
      window.kakao.maps.event.addListener(marker, "click", () =>
        router.push(`/pharmacies/${pharmacy.id}`),
      );
      markersRef.current.push(marker);

      // types/kakao-maps.d.ts에는 Marker에 title(네이티브 툴팁) 옵션이 없어,
      // 이름표는 PharmacyMapPickerDialog.tsx와 같은 CustomOverlay 방식으로 그린다.
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

    // 지도가 그리드로 나중에 크기를 확정받는 컨테이너에 들어있어, 캐시된 내부
    // 좌표계를 다시 계산시킨 뒤 bounds를 맞춘다(components/pharmacy-map.tsx와 동일 이유).
    map.relayout();
    map.setBounds(bounds);

    return () => {
      markersRef.current.forEach((marker) => marker.setMap(null));
      markersRef.current = [];
      overlaysRef.current.forEach((overlay) => overlay.setMap(null));
      overlaysRef.current = [];
      userMarkerRef.current?.setMap(null);
      userMarkerRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, pharmacyKey, userLocationKey]);

  if (!appKey) return null;

  return (
    <div className="relative h-56 w-full overflow-hidden rounded-lg border sm:h-64">
      <Script
        src={`https://dapi.kakao.com/v2/maps/sdk.js?appkey=${appKey}&autoload=false`}
        strategy="afterInteractive"
        onLoad={() => window.kakao.maps.load(() => setReady(true))}
        onError={onLoadError}
      />
      <div ref={containerRef} className="h-full w-full" />
      {!ready ? <MapSkeleton /> : null}
    </div>
  );
}
