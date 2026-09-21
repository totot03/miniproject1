import { MapPin } from "lucide-react";

import { Skeleton } from "@/components/ui/skeleton";

/**
 * 카카오맵 SDK/컨테이너가 준비되기 전 표시하는 자리 채우기.
 *
 * 예전엔 "지도를 불러오는 중…" 텍스트 한 줄만 있었는데, 실제 지도 영역의
 * 형태(줄지어진 도로/블록)를 흉내 낸 스켈레톤으로 바꿔 로딩 → 지도 전환의
 * 시각적 이질감을 줄인다. pharmacy-map.tsx / NearbyPharmacyMap.tsx /
 * PharmacyMapPickerDialog.tsx 세 곳이 동일한 마크업을 썼던 것을 여기 하나로
 * 모은다.
 */
export function MapSkeleton() {
  return (
    <div role="status" aria-label="지도를 불러오는 중" className="absolute inset-0">
      <Skeleton className="absolute inset-0 rounded-none" />
      <MapPin
        aria-hidden="true"
        className="text-muted-foreground/50 absolute inset-0 m-auto size-8"
      />
    </div>
  );
}
