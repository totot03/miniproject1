import { describe, expect, it } from "vitest";

import locationReducer, {
  clearLocation,
  setCoordinates,
  setRegionFallback,
  type LocationState,
} from "@/lib/slices/locationSlice";

const initialState: LocationState = {
  lat: null,
  lng: null,
  regionCode: null,
  label: null,
  source: null,
  error: null,
};

describe("locationSlice", () => {
  it("setCoordinates는 GPS 좌표를 반영하고 regionCode를 비운다", () => {
    const state = locationReducer(
      initialState,
      setCoordinates({ lat: 37.4979, lng: 127.0276 }),
    );

    expect(state).toEqual({
      lat: 37.4979,
      lng: 127.0276,
      regionCode: null,
      label: null,
      source: "GPS",
      error: null,
    });
  });

  it("setCoordinates는 label을 생략하면 null로 둔다", () => {
    const state = locationReducer(
      initialState,
      setCoordinates({ lat: 37.4979, lng: 127.0276, label: "내 위치" }),
    );

    expect(state.label).toBe("내 위치");
  });

  it("setRegionFallback은 지역 폴백 좌표를 반영한다", () => {
    const state = locationReducer(
      initialState,
      setRegionFallback({
        lat: 37.4959,
        lng: 127.0664,
        regionCode: "11680",
        label: "서울 강남구",
      }),
    );

    expect(state).toEqual({
      lat: 37.4959,
      lng: 127.0664,
      regionCode: "11680",
      label: "서울 강남구",
      source: "REGION",
      error: null,
    });
  });

  it("GPS 좌표 이후 지역 폴백으로 바꾸면 이전 GPS 값이 깨끗이 대체된다", () => {
    const afterGps = locationReducer(
      initialState,
      setCoordinates({ lat: 37.4979, lng: 127.0276, label: "내 위치" }),
    );

    const afterFallback = locationReducer(
      afterGps,
      setRegionFallback({
        lat: 37.4959,
        lng: 127.0664,
        regionCode: "11680",
        label: "서울 강남구",
      }),
    );

    expect(afterFallback.source).toBe("REGION");
    expect(afterFallback.regionCode).toBe("11680");
    expect(afterFallback.label).toBe("서울 강남구");
  });

  it("clearLocation은 초기 상태로 되돌린다", () => {
    const afterGps = locationReducer(
      initialState,
      setCoordinates({ lat: 37.4979, lng: 127.0276 }),
    );

    expect(locationReducer(afterGps, clearLocation())).toEqual(initialState);
  });
});
