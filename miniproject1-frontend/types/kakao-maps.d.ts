/**
 * 카카오맵 JS SDK 최소 앰비언트 타입 (docs/ROADMAP.md T-22).
 *
 * 공식/커뮤니티 @types 패키지가 설치돼 있지 않고, 이번 구현(components/pharmacy-map.tsx)이
 * 실제로 호출하는 API 표면만 선언한다 — 전체 SDK 스펙을 선반영하지 않는다(YAGNI).
 */
declare namespace kakao.maps {
  function load(callback: () => void): void;

  class LatLng {
    constructor(lat: number, lng: number);
    getLat(): number;
    getLng(): number;
  }

  class LatLngBounds {
    constructor();
    extend(latlng: LatLng): void;
  }

  class Size {
    constructor(width: number, height: number);
  }

  interface MapOptions {
    center: LatLng;
    level: number;
  }

  class Map {
    constructor(container: HTMLElement, options: MapOptions);
    setBounds(bounds: LatLngBounds): void;
    setCenter(latlng: LatLng): void;
    panTo(latlng: LatLng): void;
    /** 컨테이너 크기가 생성 이후에 바뀌었을 때(그리드 stretch 등) 다시 계산시킨다. */
    relayout(): void;
  }

  interface MarkerImageOptions {
    offset?: { x: number; y: number } | Size;
  }

  class MarkerImage {
    constructor(src: string, size: Size, options?: MarkerImageOptions);
  }

  interface MarkerOptions {
    map?: Map;
    position: LatLng;
    image?: MarkerImage;
    zIndex?: number;
  }

  class Marker {
    constructor(options: MarkerOptions);
    setMap(map: Map | null): void;
    getPosition(): LatLng;
  }

  interface CustomOverlayOptions {
    position: LatLng;
    content: string | HTMLElement;
    map?: Map;
    xAnchor?: number;
    yAnchor?: number;
    zIndex?: number;
  }

  class CustomOverlay {
    constructor(options: CustomOverlayOptions);
    setMap(map: Map | null): void;
  }

  namespace event {
    function addListener(
      target: Marker | Map,
      type: string,
      handler: (...args: unknown[]) => void,
    ): void;
  }
}

interface Window {
  kakao: typeof kakao;
}
