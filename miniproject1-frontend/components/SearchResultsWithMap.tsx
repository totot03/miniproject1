"use client";

import { useMemo, useRef, useState } from "react";

import { PharmacyMap, type MapPharmacy } from "@/components/pharmacy-map";
import { isTopPick } from "@/lib/search";
import type { components } from "@/types/api";

type SearchResultItem = components["schemas"]["SearchResultItem"];

export interface SearchResultsWithMapProps {
  results: SearchResultItem[];
  userLocation?: { lat: number; lng: number };
  /** 서버(app/search/page.tsx)가 렌더링한 PharmacyResultCard 목록. */
  children: React.ReactNode;
}

type MapEligibleItem = SearchResultItem & {
  pharmacy: NonNullable<SearchResultItem["pharmacy"]> & {
    id: number;
    lat: number;
    lng: number;
  };
  price: NonNullable<SearchResultItem["price"]> & { repPrice: number };
};

/**
 * PharmacyResultCard.tsx의 hasRequiredFields와 같은 방어적 필터링 스타일이다.
 * lat/lng가 없는 항목은 카드에는 뜨지만 지도 마커에서는 조용히 빠진다.
 */
function hasMapFields(item: SearchResultItem): item is MapEligibleItem {
  return (
    typeof item.pharmacy?.id === "number" &&
    typeof item.pharmacy?.lat === "number" &&
    typeof item.pharmacy?.lng === "number" &&
    typeof item.price?.repPrice === "number"
  );
}

/**
 * 검색 결과 리스트 + 지도 레이아웃 (docs/ROADMAP.md T-22).
 *
 * PharmacyResultCard(서버 컴포넌트)는 여기서 직접 import하지 않고 children으로만
 * 받는다 — import하면 카드가 클라이언트 번들에 편입돼 "상호작용이 없어 서버
 * 컴포넌트로 둔다"는 원래 설계 의도가 깨진다. 카드 쪽 연동은 카드 최상위 요소의
 * data-pharmacy-id 속성 하나에 이벤트 위임을 걸어서 해결한다(개별 카드에 핸들러를
 * 붙이지 않아도 되므로 카드는 여전히 순수 정적 HTML로 남는다).
 *
 * 지도 상호작용 상태(activeId, mapFailed)는 이 페이지에만 쓰이는 휘발성 UI 상태라
 * Redux에 넣지 않는다.
 */
export function SearchResultsWithMap({
  results,
  userLocation,
  children,
}: SearchResultsWithMapProps) {
  const [activeId, setActiveId] = useState<number | null>(null);
  const [mapFailed, setMapFailed] = useState(
    !process.env.NEXT_PUBLIC_KAKAO_MAP_KEY,
  );
  const listRef = useRef<HTMLDivElement | null>(null);

  const markers = useMemo<MapPharmacy[]>(
    () =>
      results.filter(hasMapFields).map((item) => ({
        id: item.pharmacy.id,
        lat: item.pharmacy.lat,
        lng: item.pharmacy.lng,
        price: item.price.repPrice,
        isTopPick: isTopPick(item),
      })),
    [results],
  );

  /** mouseover/click을 리스트 컨테이너 하나에서만 위임 처리한다. */
  function handleListEvent(event: React.SyntheticEvent<HTMLDivElement>) {
    const card = (event.target as HTMLElement).closest(
      "[data-pharmacy-id]",
    ) as HTMLElement | null;
    if (!card) return;
    const id = Number(card.getAttribute("data-pharmacy-id"));
    if (Number.isFinite(id)) setActiveId(id);
  }

  /** 마커 클릭 시 지도 쪽 강조는 PharmacyMap이 activeId로 알아서 반영하고, 여기서는 리스트 스크롤만 챙긴다. */
  function handleMarkerSelect(id: number) {
    setActiveId(id);
    listRef.current
      ?.querySelector(`[data-pharmacy-id="${id}"]`)
      ?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  return (
    <div
      // grid-cols-1(= grid-template-columns: minmax(0, 1fr))이 없으면 트랙이
      // 카드 내용의 max-content 너비까지 늘어나 375px에서 페이지 전체가
      // 가로로 밀린다(docs/ROADMAP.md T-36 실측 — "grid 블로우아웃").
      className={
        mapFailed
          ? "grid grid-cols-1 gap-6"
          : "grid grid-cols-1 gap-6 lg:grid-cols-[1fr_360px]"
      }
    >
      <div
        ref={listRef}
        className="space-y-3"
        onMouseOver={handleListEvent}
        onClick={handleListEvent}
      >
        {children}
      </div>

      {mapFailed ? null : (
        <aside className="bg-muted/30 hidden rounded-lg border lg:block">
          <PharmacyMap
            pharmacies={markers}
            userLocation={userLocation}
            activeId={activeId}
            onHoverPharmacy={setActiveId}
            onSelectPharmacy={handleMarkerSelect}
            onLoadError={() => setMapFailed(true)}
          />
        </aside>
      )}
    </div>
  );
}
