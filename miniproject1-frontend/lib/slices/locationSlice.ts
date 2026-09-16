import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

/**
 * 사용자 위치 상태.
 *
 * 화면 간 공유되는 클라이언트 상태이므로 Redux가 맡는다 (docs/PRD.md §4.2).
 * 단, 검색 URL에 들어가는 lat/lng/radius는 URL 쿼리가 정본이다.
 * 이 슬라이스는 "마지막으로 취득한 위치"를 기억해 검색 링크를 만들 때 쓴다.
 *
 * 실제 위치 취득(navigator.geolocation)과 시·군·구 폴백은 T-16에서 채운다.
 *
 * 이 슬라이스는 "확정된" 위치만 담는다. GPS 요청 중/거부/타임아웃 같은
 * 일시적 진행 상태는 hooks/useUserLocation.ts가 로컬 상태로만 관리하고
 * Redux에는 올리지 않는다 — 다른 화면은 "지금 요청 중인지"를 알 필요가
 * 없고, 최종적으로 확정된 좌표만 알면 된다.
 */

/** 좌표 출처. docs/API.md §5 query.locationSource와 같은 값 */
export type LocationSource = "GPS" | "REGION";

export interface LocationState {
  lat: number | null;
  lng: number | null;
  /** 위치 권한 거부 시 선택한 시·군·구 코드 */
  regionCode: string | null;
  /** 사용자에게 보여줄 지역명 (예: "서울 강남구") */
  label: string | null;
  source: LocationSource | null;
  /** 권한 거부·취득 실패 사유 */
  error: string | null;
}

const initialState: LocationState = {
  lat: null,
  lng: null,
  regionCode: null,
  label: null,
  source: null,
  error: null,
};

const locationSlice = createSlice({
  name: "location",
  initialState,
  reducers: {
    /** GPS로 취득한 좌표를 반영한다 (T-16) */
    setCoordinates(
      state,
      action: PayloadAction<{ lat: number; lng: number; label?: string }>,
    ) {
      state.lat = action.payload.lat;
      state.lng = action.payload.lng;
      state.label = action.payload.label ?? null;
      state.regionCode = null;
      state.source = "GPS";
      state.error = null;
    },
    /** 위치 권한 거부·실패 시 RegionPicker에서 고른 시·군·구 폴백 좌표를 반영한다 (T-16) */
    setRegionFallback(
      state,
      action: PayloadAction<{
        lat: number;
        lng: number;
        regionCode: string;
        label: string;
      }>,
    ) {
      state.lat = action.payload.lat;
      state.lng = action.payload.lng;
      state.regionCode = action.payload.regionCode;
      state.label = action.payload.label;
      state.source = "REGION";
      state.error = null;
    },
    /** 헤더의 "위치 재설정"에서 호출한다. 확정 상태를 비워 다시 GPS 요청/RegionPicker로 돌아간다 (T-16) */
    clearLocation() {
      return initialState;
    },
  },
});

export const { setCoordinates, setRegionFallback, clearLocation } =
  locationSlice.actions;
export default locationSlice.reducer;
