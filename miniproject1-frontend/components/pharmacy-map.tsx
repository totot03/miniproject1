"use client";

import Script from "next/script";
import { useEffect, useRef, useState } from "react";

import { MapSkeleton } from "@/components/common/MapSkeleton";
import { formatNumber } from "@/lib/format";

export interface MapPharmacy {
  id: number;
  lat: number;
  lng: number;
  price: number;
  isTopPick: boolean;
}

export interface PharmacyMapProps {
  pharmacies: MapPharmacy[];
  userLocation?: { lat: number; lng: number };
  /** hover 또는 클릭으로 활성화된 약국 id. 리스트↔마커 공유 상태로, 상위 컴포넌트가 소유한다. */
  activeId: number | null;
  onHoverPharmacy: (id: number | null) => void;
  onSelectPharmacy: (id: number) => void;
  /** 키 누락 또는 SDK 로드 실패 시 1회 호출된다. 그리드를 접을지는 상위(SearchResultsWithMap)가 결정한다 — 이 컴포넌트는 지도 자신의 성패만 보고한다. */
  onLoadError: () => void;
}

/** 서울시청 — 지도 생성 시 임시 중심점. 데이터가 있으면 아래 setBounds가 즉시 덮어쓴다. */
const DEFAULT_CENTER = { lat: 37.5665, lng: 126.978 };

/** "현재 위치" 마커는 가격 의미 색 토큰(PRD §8)과 무관한 지도 관용색이라 고정값을 쓴다. */
const USER_LOCATION_COLOR = "#2563eb";

function readCssVar(name: string, fallback: string): string {
  if (typeof window === "undefined") return fallback;
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
  return value || fallback;
}

function pinMarkerImage(color: string, size: number): kakao.maps.MarkerImage {
  const height = Math.round(size * 1.3);
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${height}" viewBox="0 0 24 32">` +
    `<path d="M12 0C5.4 0 0 5.4 0 12c0 9 12 20 12 20s12-11 12-20C24 5.4 18.6 0 12 0z" fill="${color}"/>` +
    `<circle cx="12" cy="12" r="5" fill="white"/></svg>`;
  const src = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
  return new window.kakao.maps.MarkerImage(src, new window.kakao.maps.Size(size, height));
}

/** 약국 마커(핀 모양)와 확실히 구분되도록 원 모양으로 그린다(ROADMAP T-22 "별도 아이콘"). */
function userLocationImage(): kakao.maps.MarkerImage {
  const size = 18;
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">` +
    `<circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 2}" fill="${USER_LOCATION_COLOR}" stroke="white" stroke-width="3"/></svg>`;
  const src = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
  return new window.kakao.maps.MarkerImage(src, new window.kakao.maps.Size(size, size));
}

/**
 * 가격 라벨(CustomOverlay 내용)을 실제 DOM 엘리먼트로 만든다.
 *
 * kakao 지도가 콘텐츠를 문서에 그대로 붙이므로 Tailwind 클래스도 동작은 하겠지만,
 * 이 라벨은 화면 어디에도 없던 새 UI라 안전하게 인라인 style + CSS 커스텀 프로퍼티로
 * 앱 색상 토큰만 재사용한다(다크모드는 T-04 결정으로 미지원이라 별도 대응 불필요).
 */
function buildPriceOverlay(price: number, isTopPick: boolean): HTMLDivElement {
  const primary = readCssVar("--primary", "#171717");
  const primaryForeground = readCssVar("--primary-foreground", "#fafafa");
  const border = readCssVar("--border", "#e5e5e5");
  const card = readCssVar("--card", "#ffffff");
  const cardForeground = readCssVar("--card-foreground", "#171717");

  const el = document.createElement("div");
  el.textContent = `${formatNumber(price)}원`;
  el.style.padding = "2px 6px";
  el.style.borderRadius = "6px";
  el.style.fontSize = "11px";
  el.style.fontWeight = "700";
  el.style.lineHeight = "1.4";
  el.style.whiteSpace = "nowrap";
  el.style.transform = "translateY(-6px)";
  el.style.boxShadow = "0 1px 2px rgba(0,0,0,0.18)";
  el.style.border = `1px solid ${border}`;
  el.style.color = isTopPick ? primaryForeground : cardForeground;
  el.style.background = isTopPick ? primary : card;
  return el;
}

/**
 * pharmacies 배열의 "내용"이 바뀌었을 때만 지도를 다시 그리기 위한 안정적인 키.
 * PharmacyResultCard.tsx의 hasRequiredFields와 같은 방어적 필드 접근 스타일이다.
 */
function pharmacyKeyOf(pharmacies: MapPharmacy[]): string {
  return pharmacies
    .map((p) => `${p.id}:${p.lat}:${p.lng}:${p.price}:${p.isTopPick}`)
    .join("|");
}

/**
 * 검색 결과 지도 (docs/ROADMAP.md T-22).
 *
 * 이 컴포넌트는 지도 자체의 렌더링/SDK 로딩만 책임진다. 그리드 레이아웃을 접을지,
 * hover/선택 상태를 어디에 저장할지는 상위(components/SearchResultsWithMap.tsx)가
 * 결정한다 — 이 컴포넌트는 activeId를 받아 반영만 하고, onHoverPharmacy/onSelectPharmacy/
 * onLoadError로 이벤트만 위로 올린다.
 */
export function PharmacyMap({
  pharmacies,
  userLocation,
  activeId,
  onHoverPharmacy,
  onSelectPharmacy,
  onLoadError,
}: PharmacyMapProps) {
  const appKey = process.env.NEXT_PUBLIC_KAKAO_MAP_KEY;

  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<kakao.maps.Map | null>(null);
  const markersRef = useRef(
    new Map<
      number,
      { marker: kakao.maps.Marker; overlay: kakao.maps.CustomOverlay; overlayEl: HTMLDivElement }
    >(),
  );
  const userMarkerRef = useRef<kakao.maps.Marker | null>(null);
  const activeMarkerIdRef = useRef<number | null>(null);
  const onHoverRef = useRef(onHoverPharmacy);
  const onSelectRef = useRef(onSelectPharmacy);
  const [ready, setReady] = useState(false);

  // 마커 리스너는 최초 생성 시 한 번만 클로저를 캡처하므로, 항상 최신 콜백을
  // 부르도록 ref에 동기화한다 — 그래야 콜백 정체성이 바뀌어도 마커를 다시 그리지 않는다.
  useEffect(() => {
    onHoverRef.current = onHoverPharmacy;
  }, [onHoverPharmacy]);
  useEffect(() => {
    onSelectRef.current = onSelectPharmacy;
  }, [onSelectPharmacy]);

  // 키 자체가 없으면 스크립트를 그리지도 않고 곧바로 실패를 알린다. 마운트 시 1회만
  // 판단하면 되는 값이라 의도적으로 빈 deps를 쓴다.
  useEffect(() => {
    if (!appKey) onLoadError();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // components/location/NearbyPharmacyMap.tsx와 같은 이유 — 스크립트는
  // 로드됐지만(도메인 미등록 등) kakao.maps.load 콜백이 끝내 안 불리는
  // 경우를 대비한 안전망이다.
  useEffect(() => {
    if (!appKey || ready) return;
    const timer = setTimeout(onLoadError, 8000);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, appKey]);

  // React(Strict Mode) 개발 모드는 mount → cleanup → mount를 한 번 더 시뮬레이션한다.
  // cleanup에서 mapRef를 반드시 비워야 두 번째 mount가 새 지도를 만들고, 낡은 지도
  // 인스턴스가 감춰진 컨테이너를 붙든 채 남는 일이 없다.
  useEffect(() => {
    if (!ready || !containerRef.current || mapRef.current) return;
    mapRef.current = new window.kakao.maps.Map(containerRef.current, {
      center: new window.kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
      level: 5,
    });
    return () => {
      mapRef.current = null;
    };
  }, [ready]);

  const pharmacyKey = pharmacyKeyOf(pharmacies);
  const userLocationKey = userLocation ? `${userLocation.lat}:${userLocation.lng}` : "";

  function clearMarkers() {
    markersRef.current.forEach(({ marker, overlay }) => {
      marker.setMap(null);
      overlay.setMap(null);
    });
    markersRef.current.clear();
    userMarkerRef.current?.setMap(null);
    userMarkerRef.current = null;
  }

  // pharmacies/userLocation은 상위가 매 렌더 새 배열/객체를 넘길 수 있으므로, 참조가
  // 아니라 내용을 요약한 원시값 키에 의존한다 — 그래야 hover만으로 지도가 통째로 다시
  // 그려지는 낭비가 없다. 정리(clearMarkers)는 cleanup 함수 하나로만 하고 effect 본문
  // 맨 위에서 다시 부르지 않는다 — 의존값이 바뀌어 재실행될 때도 React가 이전 cleanup을
  // 먼저 불러주므로 중복 정리 코드가 필요 없다.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const bounds = new window.kakao.maps.LatLngBounds();
    let hasBoundsPoint = false;

    if (userLocation) {
      const position = new window.kakao.maps.LatLng(userLocation.lat, userLocation.lng);
      userMarkerRef.current = new window.kakao.maps.Marker({
        map,
        position,
        image: userLocationImage(),
        zIndex: 10,
      });
      bounds.extend(position);
      hasBoundsPoint = true;
    }

    for (const pharmacy of pharmacies) {
      const position = new window.kakao.maps.LatLng(pharmacy.lat, pharmacy.lng);
      bounds.extend(position);
      hasBoundsPoint = true;

      const pinColor = readCssVar(
        pharmacy.isTopPick ? "--primary" : "--muted-foreground",
        pharmacy.isTopPick ? "#171717" : "#a3a3a3",
      );
      const marker = new window.kakao.maps.Marker({
        map,
        position,
        image: pinMarkerImage(pinColor, pharmacy.isTopPick ? 34 : 24),
        zIndex: pharmacy.isTopPick ? 5 : 1,
      });

      const overlayEl = buildPriceOverlay(pharmacy.price, pharmacy.isTopPick);
      const overlay = new window.kakao.maps.CustomOverlay({
        map,
        position,
        content: overlayEl,
        yAnchor: pharmacy.isTopPick ? 2.6 : 2.1,
        zIndex: pharmacy.isTopPick ? 6 : 2,
      });

      const pharmacyId = pharmacy.id;
      window.kakao.maps.event.addListener(marker, "click", () => onSelectRef.current(pharmacyId));
      window.kakao.maps.event.addListener(marker, "mouseover", () => onHoverRef.current(pharmacyId));
      window.kakao.maps.event.addListener(marker, "mouseout", () => onHoverRef.current(null));

      markersRef.current.set(pharmacyId, { marker, overlay, overlayEl });
    }

    if (hasBoundsPoint) {
      // 지도가 그리드 stretch로 크기를 나중에 확정받는 컨테이너에 들어있어, 생성 시점의
      // 치수로 캐시된 내부 좌표계를 여기서 강제로 다시 계산시킨 뒤 bounds를 맞춘다.
      // 이걸 빼먹으면 실제 마커는 좁은 범위에 모여 있는데도 지도가 전국 단위로 보인다.
      map.relayout();
      map.setBounds(bounds);
    }

    return clearMarkers;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, pharmacyKey, userLocationKey]);

  // hover/클릭으로 활성 약국이 바뀌면 마커로 지도 중심을 옮기고 가격 라벨을 강조한다.
  useEffect(() => {
    const previous = activeMarkerIdRef.current;
    if (previous != null && previous !== activeId) {
      markersRef.current.get(previous)?.overlayEl.style.removeProperty("outline");
    }
    if (activeId != null) {
      const entry = markersRef.current.get(activeId);
      if (entry) {
        entry.overlayEl.style.outline = `2px solid ${readCssVar("--ring", "#a3a3a3")}`;
        mapRef.current?.panTo(entry.marker.getPosition());
      }
    }
    activeMarkerIdRef.current = activeId;
  }, [activeId]);

  // 키가 없으면 렌더 자체를 하지 않는다 — 상위가 onLoadError를 받아 그리드를 접는다.
  if (!appKey) return null;

  return (
    <div className="relative h-full w-full">
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
