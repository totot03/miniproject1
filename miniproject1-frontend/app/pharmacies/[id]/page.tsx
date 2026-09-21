import Link from "next/link";
import { Clock } from "lucide-react";

import { DrugPriceRow } from "@/components/DrugPriceRow";
import { EmptyState } from "@/components/common/EmptyState";
import { Button } from "@/components/ui/button";
import { apiFetch } from "@/lib/api";
import { formatDistance } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { components } from "@/types/api";

type PharmacyDetailResponse = components["schemas"]["PharmacyDetailResponse"];

/** docs/API.md §4 businessHours 키 순서 + 한글 라벨. */
const DAY_LABELS: readonly [key: string, label: string][] = [
  ["mon", "월"],
  ["tue", "화"],
  ["wed", "수"],
  ["thu", "목"],
  ["fri", "금"],
  ["sat", "토"],
  ["sun", "일"],
  ["holiday", "공휴일"],
];

/** Intl "short" 요일 표기(ko-KR)는 DAY_LABELS의 라벨("월","화",...)과 정확히 같은 문자열을 낸다. */
const KOREAN_WEEKDAY_TO_KEY: Record<string, string> = {
  월: "mon",
  화: "tue",
  수: "wed",
  목: "thu",
  금: "fri",
  토: "sat",
  일: "sun",
};

/** 서버 렌더 시점(Asia/Seoul) 기준 오늘의 businessHours 키. 타임존이 어긋나면 "오늘" 강조가 하루 밀릴 수 있어 명시적으로 고정한다. */
function todayKey(): string {
  const weekday = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    weekday: "short",
  }).format(new Date());
  return KOREAN_WEEKDAY_TO_KEY[weekday] ?? "";
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** T-22(카카오맵 SDK) 이전이라 SDK 없이도 되는 카카오맵 길찾기 웹 URL만 쓴다. */
function kakaoDirectionsUrl(name: string, lat: number, lng: number): string {
  return `https://map.kakao.com/link/to/${encodeURIComponent(name)},${lat},${lng}`;
}

/**
 * 약국 상세 화면 (docs/ROADMAP.md T-21).
 *
 * app/search/page.tsx와 동일하게 서버 컴포넌트 SSR + apiFetch로 처리한다.
 * 404(PHARMACY_NOT_FOUND)를 포함한 실패는 apiFetch가 던지는 ApiError를
 * app/pharmacies/[id]/error.tsx가 받아 처리한다.
 */
export default async function PharmacyDetailPage(
  props: PageProps<"/pharmacies/[id]">,
) {
  const { id } = await props.params;
  const searchParams = await props.searchParams;
  const lat = first(searchParams.lat);
  const lng = first(searchParams.lng);

  const query = new URLSearchParams();
  if (lat && lng) {
    query.set("lat", lat);
    query.set("lng", lng);
  }
  const qs = query.toString();

  const pharmacy = await apiFetch<PharmacyDetailResponse>(
    `/api/v1/pharmacies/${id}${qs ? `?${qs}` : ""}`,
  );

  const pharmacyId = Number(id);
  const drugPrices = pharmacy.drugPrices ?? [];
  const hasCoordinates = typeof pharmacy.lat === "number" && typeof pharmacy.lng === "number";

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-8">
      <div className="space-y-2">
        <h1 className="text-xl font-bold tracking-tight sm:text-2xl">{pharmacy.name}</h1>
        {pharmacy.addressRoad ? (
          <p className="text-muted-foreground text-sm">{pharmacy.addressRoad}</p>
        ) : null}

        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
          {pharmacy.phone ? (
            <a href={`tel:${pharmacy.phone}`} className="text-primary hover:underline">
              {pharmacy.phone}
            </a>
          ) : null}
          {pharmacy.distanceM != null ? (
            <span className="text-muted-foreground">{formatDistance(pharmacy.distanceM)}</span>
          ) : null}
          {hasCoordinates ? (
            <a
              href={kakaoDirectionsUrl(pharmacy.name ?? "", pharmacy.lat as number, pharmacy.lng as number)}
              target="_blank"
              rel="noreferrer noopener"
              className="text-primary hover:underline"
            >
              카카오맵 길찾기
            </a>
          ) : null}
        </div>

        {pharmacy.businessHours ? (
          <div className="bg-muted/30 max-w-xs rounded-lg border p-3 pt-1">
            <div className="text-muted-foreground flex items-center gap-1.5 pt-2 pb-1.5 text-xs font-semibold">
              <Clock className="size-3.5" />
              영업시간
            </div>
            <dl className="space-y-0.5 text-xs">
              {DAY_LABELS.map(([key, label]) => {
                const hours = pharmacy.businessHours?.[key];
                const isOpen = hours && hours.length === 2;
                const isToday = key === todayKey();
                return (
                  <div
                    key={key}
                    className={cn(
                      "flex items-center justify-between gap-3 rounded px-1.5 py-1",
                      isToday && "bg-primary/10",
                    )}
                  >
                    <dt className={cn("font-medium", isToday && "text-primary")}>
                      {label}
                      {isToday ? (
                        <span className="ml-1 text-[10px] font-normal">오늘</span>
                      ) : null}
                    </dt>
                    <dd
                      className={cn(
                        "tabular-nums",
                        isOpen ? "text-foreground" : "text-muted-foreground",
                      )}
                    >
                      {isOpen ? `${hours[0]} ~ ${hours[1]}` : "휴무"}
                    </dd>
                  </div>
                );
              })}
            </dl>
          </div>
        ) : null}
      </div>

      {/* docs/ROADMAP.md T-29 3번 — 약국 상세에서 넘어가면 이 약국이 미리 채워진다. */}
      <Button asChild className="w-fit">
        <Link
          href={`/reports/new?pharmacyId=${pharmacyId}&pharmacyName=${encodeURIComponent(pharmacy.name ?? "")}`}
        >
          이 약국에 가격 제보하기
        </Link>
      </Button>

      <div className="space-y-2">
        <h2 className="text-lg font-semibold">취급 약품 가격</h2>
        {drugPrices.length === 0 ? (
          <EmptyState
            title="등록된 가격 정보가 없습니다"
            description="아직 이 약국에 대한 가격 제보가 없습니다."
          />
        ) : (
          // 컬럼이 5개라 375px에서는 테이블이 카드 폭보다 넓어질 수 있다 —
          // 페이지 자체가 아니라 이 테이블만 가로로 스크롤되게 한다
          // (components/charts/RegionStatsTable.tsx와 같은 패턴, docs/ROADMAP.md T-36).
          <div className="overflow-x-auto">
            <table className="w-full min-w-[520px] border-collapse text-sm">
              <thead>
                <tr className="text-muted-foreground border-b text-left text-xs">
                  <th className="pb-2 font-medium">약품</th>
                  <th className="pb-2 text-right font-medium">대표가격</th>
                  <th className="pb-2 text-right font-medium">제보 수</th>
                  <th className="pb-2 text-right font-medium">최근 갱신</th>
                  <th className="pb-2 text-right font-medium">전국 평균 대비</th>
                </tr>
              </thead>
              <tbody>
                {drugPrices.map((drugPrice, index) => (
                  <DrugPriceRow
                    key={drugPrice.drugId ?? index}
                    pharmacyId={pharmacyId}
                    drugPrice={drugPrice}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
