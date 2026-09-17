import Link from "next/link";

import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { DistanceBadge } from "@/components/common/DistanceBadge";
import { PriceTag } from "@/components/common/PriceTag";
import { formatNumber, formatRelativeDate } from "@/lib/format";
import { isTopPick } from "@/lib/search";
import { cn } from "@/lib/utils";
import type { components } from "@/types/api";

type SearchResultItem = components["schemas"]["SearchResultItem"];

export interface PharmacyResultCardProps {
  item: SearchResultItem;
  className?: string;
}

type RequiredResultItem = SearchResultItem & {
  rank: number;
  pharmacy: NonNullable<SearchResultItem["pharmacy"]> & {
    id: number;
    name: string;
    addressRoad: string;
  };
  price: NonNullable<SearchResultItem["price"]> & {
    repPrice: number;
    minPrice: number;
    reportCount: number;
    lastReportedAt: string;
  };
  distanceM: number;
};

/**
 * openapi-typescript는 필수 여부를 모르면 전부 optional로 만든다 — 카드 렌더링에
 * 꼭 필요한 필드가 실제로 없을 리 없지만, 렌더링 전에 걸러낸다
 * (DrugAutocomplete.tsx hasIdAndName, RegionPicker.tsx hasSido/hasCode와 같은 패턴).
 */
function hasRequiredFields(
  item: SearchResultItem,
): item is RequiredResultItem {
  return (
    typeof item.rank === "number" &&
    typeof item.pharmacy?.id === "number" &&
    typeof item.pharmacy?.name === "string" &&
    typeof item.pharmacy?.addressRoad === "string" &&
    typeof item.price?.repPrice === "number" &&
    typeof item.price?.minPrice === "number" &&
    typeof item.price?.reportCount === "number" &&
    typeof item.price?.lastReportedAt === "string" &&
    typeof item.distanceM === "number"
  );
}

/** docs/API.md §5 badges 설명 표. LOWEST_PRICE는 카드 강조(isTopPick)로 이미 표현하므로 여기 없다. */
const BADGE_LABELS: Partial<
  Record<string, { label: string; variant: "outline" | "secondary" }>
> = {
  LOW_CONFIDENCE: { label: "정보 부족", variant: "outline" },
  STALE_DATA: { label: "오래된 정보", variant: "outline" },
  NEAREST: { label: "최단거리", variant: "secondary" },
};

/**
 * 검색 결과 카드 하나 (docs/ROADMAP.md T-18).
 *
 * 상호작용이 없어 서버 컴포넌트로 둔다 — SiteHeader가 로고·로그인 버튼을
 * 서버에서 그대로 렌더링하는 것과 같은 방식으로 클라이언트 경계를 최소화한다.
 */
export function PharmacyResultCard({
  item,
  className,
}: PharmacyResultCardProps) {
  if (!hasRequiredFields(item)) return null;

  const { pharmacy, price, distanceM, rank, recommended, badges = [] } = item;
  const topPick = isTopPick({ recommended, rank });

  return (
    <Link href={`/pharmacies/${pharmacy.id}`} className="block">
      <Card
        data-pharmacy-id={pharmacy.id}
        className={cn(
          "hover:bg-muted/30",
          topPick && "border-primary ring-primary/20 border-2 ring-2",
          className,
        )}
      >
        <CardContent className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 flex-1 space-y-1.5">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="truncate font-semibold">{pharmacy.name}</h3>
              {topPick ? <Badge>최저가 추천</Badge> : null}
              {badges.map((badge) => {
                const meta = BADGE_LABELS[badge];
                if (!meta) return null;
                return (
                  <Badge
                    key={badge}
                    variant={meta.variant}
                    className={cn(badge === "STALE_DATA" && "text-stale")}
                  >
                    {meta.label}
                  </Badge>
                );
              })}
            </div>
            <p className="text-muted-foreground truncate text-sm">
              {pharmacy.addressRoad}
            </p>
            <div className="text-muted-foreground flex flex-wrap items-center gap-2 text-xs">
              <DistanceBadge meters={distanceM} />
              <span>제보 {price.reportCount}건</span>
              <span>{formatRelativeDate(price.lastReportedAt)} 갱신</span>
            </div>
          </div>

          <div className="shrink-0 space-y-1 text-right">
            <PriceTag price={price.repPrice} size="lg" lowest={topPick} />
            {price.minPrice < price.repPrice ? (
              <p className="text-muted-foreground text-xs">
                최저 <PriceTag price={price.minPrice} size="sm" />
              </p>
            ) : null}
            {price.savingVsCandidateAvg != null &&
            price.savingVsCandidateAvg > 0 ? (
              <p className="text-price-lowest text-xs font-medium">
                평균보다 {formatNumber(price.savingVsCandidateAvg)}원 저렴
              </p>
            ) : null}
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
