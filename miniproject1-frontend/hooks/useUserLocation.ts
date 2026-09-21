"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import {
  LOCATION_STORAGE_KEY,
  mapGeolocationErrorToStatus,
  parseStoredLocation,
  serializeLocation,
  type StoredLocation,
} from "@/lib/geolocation";
import { useAppDispatch, useAppSelector } from "@/lib/hooks";
import {
  clearLocation,
  setCoordinates,
  setRegionFallback,
  type LocationSource,
} from "@/lib/slices/locationSlice";

/**
 * useUserLocation 훅의 로컬 진행 상태.
 *
 * docs/ROADMAP.md T-16 의사코드의 LocationState와 같은 모양이지만, 이름이
 * lib/slices/locationSlice.ts의 LocationState(Redux, "확정된 결과"만 담음)와
 * 겹치므로 여기서는 UseUserLocationState로 구분한다.
 */
export type UseUserLocationState =
  | { status: "idle" | "requesting" }
  | { status: "granted"; lat: number; lng: number; source: "GPS" }
  | {
      status: "fallback";
      lat: number;
      lng: number;
      source: "REGION";
      regionCode: string;
    }
  | { status: "denied" | "unavailable" };

/**
 * ROADMAP T-16 메모(저정밀 기본값)는 폐기한다 — enableHighAccuracy: false는
 * GPS 칩 대신 Wi-Fi/기지국(또는 그마저 없으면 IP) 기반 추정치를 쓰게 되는데,
 * 국내에서는 이 추정치가 실제 위치와 수백m~수km씩 어긋나는 경우가 많아
 * "현재 위치" 버튼의 이름과 어긋난다. 이 옵션은 항상 사용자가 버튼을 눌러야만
 * 쓰이므로(자동/조용한 요청 없음, NearbyPharmacies/SearchLocationBar/
 * DrugAutocomplete 참고) 정확도를 위해 몇 초 더 기다리는 편이 낫다.
 */
const GEOLOCATION_OPTIONS: PositionOptions = {
  enableHighAccuracy: true,
  timeout: 15000,
};

function readStoredLocation(): StoredLocation | null {
  if (typeof window === "undefined") return null;
  try {
    return parseStoredLocation(
      window.sessionStorage.getItem(LOCATION_STORAGE_KEY),
    );
  } catch {
    // 시크릿 모드·사이트 데이터 차단 시 getItem 자체가 예외를 던질 수 있다.
    return null;
  }
}

function writeStoredLocation(location: StoredLocation): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(
      LOCATION_STORAGE_KEY,
      serializeLocation(location),
    );
  } catch {
    // 저장 실패는 조용히 무시한다 — 이번 세션 새로고침에서만 위치가 안 남을 뿐이다.
  }
}

function clearStoredLocation(): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.removeItem(LOCATION_STORAGE_KEY);
  } catch {
    // 무시
  }
}

function toUseUserLocationState(input: {
  lat: number;
  lng: number;
  regionCode: string | null;
  source: LocationSource;
}): UseUserLocationState {
  if (input.source === "GPS") {
    return { status: "granted", lat: input.lat, lng: input.lng, source: "GPS" };
  }
  return {
    status: "fallback",
    lat: input.lat,
    lng: input.lng,
    source: "REGION",
    regionCode: input.regionCode ?? "",
  };
}

/**
 * 사용자 위치를 취득하고, 화면 간 공유 상태(Redux locationSlice)와
 * 새로고침 복원(sessionStorage)을 함께 관리한다 (docs/ROADMAP.md T-16).
 *
 * 헤더·홈·검색·제보 폼 등 여러 컴포넌트가 동시에 이 훅을 부를 수 있으므로,
 * "확정된 위치"는 항상 Redux를 정본으로 파생시킨다 — 한 컴포넌트가
 * RegionPicker로 위치를 바꾸면 다른 컴포넌트의 훅 인스턴스도 다음 렌더에
 * 자동으로 같은 값을 본다. `requesting`/`denied`/`unavailable`처럼 일시적인
 * 진행 상태만 이 훅의 로컬 state(`transientStatus`)로 둔다.
 *
 * 거부/실패 시 모달을 띄우는 것은 이 훅의 책임이 아니다 — 상태만 돌려주고,
 * 사용하는 컴포넌트(LocationIndicator)가 RegionPicker를 열지 결정한다.
 */
export function useUserLocation() {
  const dispatch = useAppDispatch();
  const reduxLocation = useAppSelector((state) => state.location);
  const [transientStatus, setTransientStatus] = useState<
    "idle" | "requesting" | "denied" | "unavailable"
  >("idle");
  // 방금 받은 GPS 응답의 오차 반경(미터). 데스크톱/노트북처럼 GPS 칩이 없는
  // 기기는 Wi-Fi/IP 기반 추정만 가능해 국내에서는 이 값이 수 km까지도 커질
  // 수 있다 — "다른 지역이 나온다" 신고의 실제 원인이 이 오차인 경우가
  // 많아, 호출부가 판단해 경고를 보여줄 수 있도록 그대로 노출한다. 지역
  // 수동 선택/리셋에는 의미가 없으므로 그때는 null로 되돌린다.
  const [lastGpsAccuracyM, setLastGpsAccuracyM] = useState<number | null>(null);

  // 마운트 시 sessionStorage에 남은 확정 위치를 Redux로 복원한다
  // ("페이지를 이동해도 위치가 유지된다" 완료 판정). Redux가 이미 값을
  // 갖고 있으면(다른 컴포넌트 인스턴스가 먼저 복원) 건드리지 않는다.
  // reduxLocation.lat/lng를 의존성에 정직하게 포함하면 값이 채워진 뒤에도
  // 이 effect가 재실행될 수 있지만, 위 가드 덕분에 매번 조용히 종료되므로
  // (idempotent) 문제가 없다.
  useEffect(() => {
    if (reduxLocation.lat !== null && reduxLocation.lng !== null) return;
    const stored = readStoredLocation();
    if (!stored) return;
    if (stored.source === "GPS") {
      dispatch(
        setCoordinates({
          lat: stored.lat,
          lng: stored.lng,
          label: stored.label ?? undefined,
        }),
      );
    } else {
      dispatch(
        setRegionFallback({
          lat: stored.lat,
          lng: stored.lng,
          regionCode: stored.regionCode ?? "",
          label: stored.label ?? "",
        }),
      );
    }
  }, [dispatch, reduxLocation.lat, reduxLocation.lng]);

  const state: UseUserLocationState = useMemo(() => {
    if (
      reduxLocation.lat !== null &&
      reduxLocation.lng !== null &&
      reduxLocation.source !== null
    ) {
      return toUseUserLocationState({
        lat: reduxLocation.lat,
        lng: reduxLocation.lng,
        regionCode: reduxLocation.regionCode,
        source: reduxLocation.source,
      });
    }
    return { status: transientStatus };
  }, [reduxLocation, transientStatus]);

  // onFailure는 거부/타임아웃/미지원 시 호출된다. RegionPicker를 여는 건
  // 이 훅이 아니라 호출부(LocationIndicator)의 책임이라 콜백으로 넘긴다 —
  // 또한 geolocation의 콜백은 비동기라, 이 콜백 안에서 부르는 setState는
  // (effect 본문에서 직접 부르는 것과 달리) React 19 set-state-in-effect
  // 규칙에 걸리지 않는 정상적인 이벤트 핸들러 패턴이다.
  const requestGpsLocation = useCallback(
    (onFailure?: (status: "denied" | "unavailable") => void) => {
      if (typeof navigator === "undefined" || !navigator.geolocation) {
        setTransientStatus("unavailable");
        onFailure?.("unavailable");
        return;
      }
      setTransientStatus("requesting");
      navigator.geolocation.getCurrentPosition(
        (position) => {
          const lat = position.coords.latitude;
          const lng = position.coords.longitude;
          setLastGpsAccuracyM(position.coords.accuracy);
          dispatch(setCoordinates({ lat, lng }));
          writeStoredLocation({
            lat,
            lng,
            regionCode: null,
            label: null,
            source: "GPS",
          });
        },
        (error) => {
          setLastGpsAccuracyM(null);
          const status = mapGeolocationErrorToStatus(error);
          setTransientStatus(status);
          onFailure?.(status);
        },
        GEOLOCATION_OPTIONS,
      );
    },
    [dispatch],
  );

  const selectRegionFallback = useCallback(
    (region: {
      regionCode: string;
      lat: number;
      lng: number;
      label: string;
    }) => {
      setLastGpsAccuracyM(null);
      dispatch(setRegionFallback(region));
      writeStoredLocation({ ...region, source: "REGION" });
    },
    [dispatch],
  );

  const reset = useCallback(() => {
    dispatch(clearLocation());
    clearStoredLocation();
    setTransientStatus("idle");
    setLastGpsAccuracyM(null);
  }, [dispatch]);

  return {
    state,
    requestGpsLocation,
    selectRegionFallback,
    reset,
    lastGpsAccuracyM,
  };
}
