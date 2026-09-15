import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

/**
 * 사용자 위치 상태.
 *
 * 화면 간 공유되는 클라이언트 상태이므로 Redux가 맡는다 (docs/PRD.md §4.2).
 * 단, 검색 URL에 들어가는 lat/lng/radius는 URL 쿼리가 정본이다.
 * 이 슬라이스는 "마지막으로 취득한 위치"를 기억해 검색 링크를 만들 때 쓴다.
 *
 * 실제 위치 취득(navigator.geolocation)과 시·군·구 폴백은 T-16에서 채운다.
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
  },
});

export const { setCoordinates } = locationSlice.actions;
export default locationSlice.reducer;
