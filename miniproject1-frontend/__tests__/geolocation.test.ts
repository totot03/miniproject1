import { describe, expect, it } from "vitest";

import {
  mapGeolocationErrorToStatus,
  parseStoredLocation,
  serializeLocation,
  type StoredLocation,
} from "@/lib/geolocation";

describe("serializeLocation / parseStoredLocation", () => {
  it("GPS 위치를 직렬화했다가 그대로 복원한다", () => {
    const location: StoredLocation = {
      lat: 37.4979,
      lng: 127.0276,
      regionCode: null,
      label: null,
      source: "GPS",
    };

    expect(parseStoredLocation(serializeLocation(location))).toEqual(location);
  });

  it("지역 폴백 위치를 직렬화했다가 그대로 복원한다", () => {
    const location: StoredLocation = {
      lat: 37.4959,
      lng: 127.0664,
      regionCode: "11680",
      label: "서울 강남구",
      source: "REGION",
    };

    expect(parseStoredLocation(serializeLocation(location))).toEqual(location);
  });

  it("null이면 null을 돌려준다 (sessionStorage에 값이 없던 경우)", () => {
    expect(parseStoredLocation(null)).toBeNull();
  });

  it("JSON이 아니면 예외 없이 null을 돌려준다", () => {
    expect(parseStoredLocation("not-json")).toBeNull();
  });

  it("필드가 기대와 다르면 null을 돌려준다 (다른 버전이 남긴 값 등)", () => {
    expect(parseStoredLocation(JSON.stringify({ lat: "37.5" }))).toBeNull();
    expect(
      parseStoredLocation(JSON.stringify({ lat: 37.5, lng: 127, source: "UNKNOWN" })),
    ).toBeNull();
  });
});

describe("mapGeolocationErrorToStatus", () => {
  it("PERMISSION_DENIED(1)는 denied로 매핑한다", () => {
    expect(mapGeolocationErrorToStatus({ code: 1 })).toBe("denied");
  });

  it("POSITION_UNAVAILABLE(2)과 TIMEOUT(3)은 unavailable로 매핑한다", () => {
    expect(mapGeolocationErrorToStatus({ code: 2 })).toBe("unavailable");
    expect(mapGeolocationErrorToStatus({ code: 3 })).toBe("unavailable");
  });
});
