import Link from "next/link";

import { DistanceBadge } from "@/components/common/DistanceBadge";
import { EmptyState } from "@/components/common/EmptyState";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { PriceTag } from "@/components/common/PriceTag";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatRelativeDate } from "@/lib/format";

import { ShowcaseErrorState } from "./_showcase/ShowcaseErrorState";

/**
 * 홈 화면.
 *
 * 지금은 서비스 설명 + T-04 공통 컴포넌트 쇼케이스다. 375px 반응형과
 * 고지 배너 노출을 눈으로 확인하는 역할을 겸한다.
 *
 * TODO(T-16): 쇼케이스 섹션을 약품 검색창(자동완성)과 인기 약품 칩으로
 * 교체한다. docs/PRD.md §7의 홈 화면 정의 참고.
 */

/** 쇼케이스용 고정 예시. 실제 데이터가 아니다. */
const SAMPLE_RESULTS = [
  {
    name: "가온약국",
    address: "서울특별시 강남구 테헤란로 123",
    repPrice: 2600,
    distanceM: 340,
    lastReportedAt: "2026-09-12",
    lowest: true,
    badges: ["최저가"],
  },
  {
    name: "새봄약국",
    address: "서울특별시 강남구 역삼로 45",
    repPrice: 2900,
    distanceM: 180,
    lastReportedAt: "2026-06-20",
    lowest: false,
    badges: ["제보 1건", "오래된 정보"],
  },
] as const;

export default function Home() {
  // 기준일을 고정해 쇼케이스 표기가 날마다 달라지지 않게 한다.
  const showcaseToday = "2026-09-15";

  return (
    <div className="mx-auto max-w-5xl space-y-10 px-4 py-8">
      <section className="space-y-3">
        <h1 className="text-2xl font-bold tracking-tight sm:text-3xl">
          동네 약국 일반의약품 최저가
        </h1>
        <p className="text-muted-foreground max-w-2xl leading-relaxed">
          같은 일반의약품이라도 약국마다 판매가가 다릅니다. 가격표가 밖에
          붙어있지 않으니 비교하려면 일일이 들어가 물어봐야 합니다. 약값알림은
          제보된 가격을 모아 <strong>가격·거리·정보 신선도</strong>를 함께
          반영해 지금 갈 만한 약국을 추천합니다.
        </p>
        <p className="text-muted-foreground text-sm">
          추천 근거는 항상 함께 보여줍니다 — 가장 싼 곳이 1위가 아닐 수도
          있습니다. 멀거나 정보가 오래됐으면 순위가 내려갑니다.
        </p>
      </section>

      {/* TODO(T-16): 아래 검색 자리를 자동완성 검색창으로 교체 */}
      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">약품 검색</h2>
        <div className="bg-muted/40 rounded-lg border border-dashed px-6 py-8 text-center">
          <p className="text-muted-foreground text-sm">
            약품 검색창과 인기 약품 칩은 T-16에서 붙입니다.
          </p>
        </div>
      </section>

      <section className="space-y-4">
        <div className="space-y-1">
          <h2 className="text-lg font-semibold tracking-tight">
            공통 컴포넌트 확인
          </h2>
          <p className="text-muted-foreground text-sm">
            T-04에서 만든 공통 컴포넌트입니다. 아래 값은 모두 표시 확인용 고정
            예시입니다.
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              검색 결과 카드 (PriceTag · DistanceBadge)
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {SAMPLE_RESULTS.map((result) => (
              <div
                key={result.name}
                className="flex flex-wrap items-start gap-x-4 gap-y-2 rounded-lg border p-4"
              >
                <div className="min-w-0 flex-1 space-y-1">
                  <p className="truncate font-medium">{result.name}</p>
                  <p className="text-muted-foreground truncate text-sm">
                    {result.address}
                  </p>
                  <div className="flex flex-wrap items-center gap-2 pt-1">
                    <DistanceBadge meters={result.distanceM} />
                    {result.badges.map((badge) => (
                      <Badge key={badge} variant="outline">
                        {badge}
                      </Badge>
                    ))}
                    <span className="text-muted-foreground text-xs">
                      {formatRelativeDate(result.lastReportedAt, showcaseToday)}{" "}
                      제보
                    </span>
                  </div>
                </div>
                <PriceTag
                  price={result.repPrice}
                  size="lg"
                  lowest={result.lowest}
                />
              </div>
            ))}
          </CardContent>
        </Card>

        <div className="grid gap-4 md:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">LoadingSkeleton</CardTitle>
            </CardHeader>
            <CardContent>
              <LoadingSkeleton count={2} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">EmptyState</CardTitle>
            </CardHeader>
            <CardContent>
              <EmptyState
                title="반경 2km 안에 제보된 가격이 없습니다"
                description="반경을 넓히면 12곳이 검색됩니다."
                action={
                  <Button variant="outline" size="sm" asChild>
                    <Link href="/">반경 5km로 넓혀보기</Link>
                  </Button>
                }
              />
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">
              ErrorState (재시도 버튼 포함)
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ShowcaseErrorState />
          </CardContent>
        </Card>
      </section>
    </div>
  );
}
