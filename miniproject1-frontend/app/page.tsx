import { DrugAutocomplete } from "@/components/DrugAutocomplete";
import { NearbyPharmacies } from "@/components/NearbyPharmacies";

/**
 * 홈 화면. 서비스 진입점 — 약품을 고르면 검색(/search)으로 넘어간다
 * (docs/ROADMAP.md T-17). "현재 위치" 버튼으로 근처 약국을 바로 둘러보는
 * 경로(NearbyPharmacies)도 약품 검색과 나란히 둔다 — 위치 재설정 기능은
 * 여기 두지 않고 약 검색 화면(components/location/SearchLocationBar.tsx)이
 * 전담한다.
 *
 * 검색 입력·자동완성 드롭다운은 전부 DrugAutocomplete가 소유한다. 이
 * 페이지는 서버 컴포넌트로 남겨두고, useRouter 등 클라이언트 훅이
 * 필요한 부분만 각 컴포넌트에 모아 "use client" 경계를 최소로 유지한다
 * (SiteHeader/AuthStatus와 같은 분리 방식).
 */
export default function Home() {
  return (
    <div className="mx-auto max-w-5xl space-y-10 px-4 py-8">
      <section>
        <h1 className="text-center text-2xl font-bold tracking-tight sm:text-3xl">
          내 주변 약국 최저가
        </h1>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">약품 검색</h2>
        <DrugAutocomplete />
      </section>

      <NearbyPharmacies />
    </div>
  );
}
