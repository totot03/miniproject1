import { MapPin } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { formatDistance } from "@/lib/format";
import { cn } from "@/lib/utils";

export interface DistanceBadgeProps {
  /** 미터 단위 거리 (docs/API.md §5 distanceM) */
  meters: number;
  className?: string;
}

/**
 * 거리 뱃지. 1000m 미만은 340m, 이상은 1.2km로 표기한다.
 */
export function DistanceBadge({ meters, className }: DistanceBadgeProps) {
  return (
    <Badge variant="secondary" className={cn("gap-1 tabular-nums", className)}>
      <MapPin aria-hidden="true" className="size-3" />
      {formatDistance(meters)}
    </Badge>
  );
}
