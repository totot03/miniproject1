/**
 * 위치 취득 관련 순수 로직.
 *
 * hooks/useUserLocation.ts가 DOM/브라우저 API(navigator.geolocation,
 * sessionStorage)를 직접 다루는 부분과, 여기 모아둔 "값 변환"을 분리한다.
 * 이 파일은 브라우저 객체를 직접 참조하지 않으므로 vitest의 기본 node
 * 환경에서 그대로 테스트할 수 있다.
 */

import type { LocationSource } from "@/lib/slices/locationSlice";

/** sessionStorage에 저장/복원하는 확정 위치. locationSlice.LocationState의 부분집합이다. */
export interface StoredLocation {
  lat: number;
  lng: number;
  regionCode: string | null;
  label: string | null;
  source: LocationSource;
}

/** sessionStorage 키. 다른 프로젝트나 향후 다른 상태와 충돌하지 않도록 접두어를 둔다. */
export const LOCATION_STORAGE_KEY = "pharmaprice:location";

function isStoredLocation(value: unknown): value is StoredLocation {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.lat === "number" &&
    typeof v.lng === "number" &&
    (v.regionCode === null || typeof v.regionCode === "string") &&
    (v.label === null || typeof v.label === "string") &&
    (v.source === "GPS" || v.source === "REGION")
  );
}

/** StoredLocation을 sessionStorage에 넣을 문자열로 직렬화한다. */
export function serializeLocation(location: StoredLocation): string {
  return JSON.stringify(location);
}

/**
 * sessionStorage에서 읽은 원문을 StoredLocation으로 되돌린다.
 *
 * 형식이 깨졌거나(다른 버전이 남긴 값, 수동 조작 등) 필드가 기대와 다르면
 * 조용히 null을 돌려준다 — 위치 복원 실패는 "새로 물어본다"로 자연스럽게
 * 이어지지, 크래시로 이어지면 안 된다.
 */
export function parseStoredLocation(raw: string | null): StoredLocation | null {
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    return isStoredLocation(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * `GeolocationPositionError`(또는 그 구조를 흉내 낸 값)의 code를 훅 상태로 매핑한다.
 *
 * 실제 `GeolocationPositionError` 타입에 의존하지 않고 `{ code: number }` 최소
 * 구조만 요구한다 — node 환경 테스트에서 평범한 객체로 호출할 수 있게 하기
 * 위해서다(실제 브라우저 에러 객체도 이 구조를 만족하므로 타입은 그대로 맞는다).
 *
 * @returns `"denied"`  — 사용자가 명시적으로 권한을 거부함(code 1, PERMISSION_DENIED)
 *          `"unavailable"` — 그 외(code 2 POSITION_UNAVAILABLE, code 3 TIMEOUT 등) — 위치를 못 구한 것은 같지만 사유가 다르다
 */
export function mapGeolocationErrorToStatus(error: {
  code: number;
}): "denied" | "unavailable" {
  const PERMISSION_DENIED = 1;
  return error.code === PERMISSION_DENIED ? "denied" : "unavailable";
}
