import Link from "next/link";

import { BusinessHours } from "@/components/BusinessHours";
import { DrugPriceRow } from "@/components/DrugPriceRow";
import { EmptyState } from "@/components/common/EmptyState";
import { Button } from "@/components/ui/button";
import { apiFetch } from "@/lib/api";
import { formatDistance } from "@/lib/format";
import type { components } from "@/types/api";

type PharmacyDetailResponse = components["schemas"]["PharmacyDetailResponse"];

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
          <BusinessHours businessHours={pharmacy.businessHours} />
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
          // 카드형 리스트라 컬럼 수와 무관하게 375px에서도 가로 스크롤이
          // 필요 없다(예전 테이블 레이아웃의 T-36 대응을 대체, DrugPriceRow 참고).
          <ul className="space-y-2">
            {drugPrices.map((drugPrice, index) => (
              <DrugPriceRow
                key={drugPrice.drugId ?? index}
                pharmacyId={pharmacyId}
                drugPrice={drugPrice}
              />
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
