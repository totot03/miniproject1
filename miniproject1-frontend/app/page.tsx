import { DrugAutocomplete } from "@/components/DrugAutocomplete";

/**
 * 홈 화면. 서비스 진입점 — 약품을 고르면 검색(/search)으로 넘어간다
 * (docs/ROADMAP.md T-17).
 *
 * 검색 입력·인기 약품 칩·자동완성 드롭다운은 전부 DrugAutocomplete가
 * 소유한다. 이 페이지는 서버 컴포넌트로 남겨두고, useRouter 등 클라이언트
 * 훅이 필요한 부분만 그 컴포넌트 하나에 모아 "use client" 경계를 최소로
 * 유지한다(SiteHeader/LocationIndicator와 같은 분리 방식).
 */
export default function Home() {
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

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">약품 검색</h2>
        <DrugAutocomplete />
      </section>
    </div>
  );
}
