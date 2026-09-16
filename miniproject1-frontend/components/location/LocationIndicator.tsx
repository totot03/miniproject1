"use client";

import { useEffect, useState } from "react";
import { MapPin, RotateCcw } from "lucide-react";

import { RegionPicker } from "@/components/location/RegionPicker";
import { useUserLocation } from "@/hooks/useUserLocation";
import { useAppSelector } from "@/lib/hooks";

function labelFor(
  status: ReturnType<typeof useUserLocation>["state"]["status"],
  storedLabel: string | null,
): string {
  switch (status) {
    case "granted":
      return storedLabel ?? "현재 위치 근처";
    case "fallback":
      return storedLabel ?? "선택한 지역";
    case "requesting":
      return "위치 확인 중...";
    case "denied":
      return "위치 접근 거부됨 · 지역 선택";
    case "unavailable":
      return "위치를 가져올 수 없음 · 지역 선택";
    case "idle":
    default:
      return "위치 설정 필요";
  }
}

/**
 * 헤더에 들어가는 위치 표시·재설정 트리거 (docs/ROADMAP.md T-16 6번).
 *
 * `SiteHeader`(서버 컴포넌트)는 이 컴포넌트만 클라이언트 경계로 감싸고
 * 나머지(로고·로그인 버튼)는 그대로 서버에서 렌더링한다.
 */
export function LocationIndicator() {
  const { state, requestGpsLocation, selectRegionFallback } =
    useUserLocation();
  const label = useAppSelector((s) => s.location.label);
  const [pickerOpen, setPickerOpen] = useState(false);

  // 진입 시 자동으로 위치 권한을 물어본다 (docs/PRD.md §7 "홈 진입 → 위치
  // 권한 허용"). status가 idle일 때만 요청하므로, state.status가 바뀔 때마다
  // 이 effect가 재실행돼도(requesting/denied/... 로 전환된 뒤에는 조건을
  // 만족하지 않아) 실제로는 최초 1회만 요청이 나간다(idempotent).
  //
  // 거부/타임아웃/미지원(onFailure)이면 즉시 지역 선택 모달을 띄운다
  // (ROADMAP T-16 3번). onFailure는 geolocation의 비동기 콜백 안에서
  // 호출되므로, 이 안의 setPickerOpen은 effect 본문의 동기 setState가
  // 아니라 이벤트 핸들러의 setState로 취급된다.
  useEffect(() => {
    if (state.status === "idle") {
      requestGpsLocation(() => setPickerOpen(true));
    }
  }, [state.status, requestGpsLocation]);

  const showResetIcon = state.status === "granted" || state.status === "fallback";

  return (
    <>
      <button
        type="button"
        onClick={() => setPickerOpen(true)}
        className="text-muted-foreground hover:text-foreground ml-auto flex items-center gap-1 text-xs transition-colors sm:text-sm"
      >
        <MapPin aria-hidden="true" className="size-4 shrink-0" />
        <span className="max-w-[9rem] truncate sm:max-w-none">
          {labelFor(state.status, label)}
        </span>
        {showResetIcon ? (
          <RotateCcw aria-hidden="true" className="size-3 shrink-0" />
        ) : null}
      </button>

      <RegionPicker
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onSelect={selectRegionFallback}
      />
    </>
  );
}
