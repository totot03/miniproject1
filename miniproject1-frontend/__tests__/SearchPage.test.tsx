// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { Provider as ReduxProvider } from "react-redux";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";

import SearchPage from "@/app/search/page";
import { apiFetch } from "@/lib/api";
import { makeStore } from "@/lib/store";
import type { components } from "@/types/api";

type SearchResponse = components["schemas"]["SearchResponse"];

// SortToggle·SearchLocationBar가 내부에서 next/navigation을 쓰므로 다른 테스트와 같은 방식으로 목킹한다.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/search",
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/lib/api", () => ({ apiFetch: vi.fn() }));
const mockedApiFetch = vi.mocked(apiFetch);

const BASE_QUERY = {
  drugId: "1",
  lat: "37.5",
  lng: "127.0",
  radius: "2000",
  sort: "SCORE",
};

function renderPage(searchParams: Record<string, string>) {
  return SearchPage({
    params: Promise.resolve({}),
    searchParams: Promise.resolve(searchParams),
  });
}

/**
 * SearchLocationBar가 항상 RegionPicker를 함께 렌더링하고, Dialog가 닫혀
 * 있어도 내부 useQuery(['regions'])는 마운트 시점에 그대로 실행된다
 * (DrugAutocomplete.test.tsx와 같은 이유). 그래서 apiFetch 목은 경로로
 * 분기해야 /api/v1/search 호출과 /api/v1/regions 호출을 섞지 않는다.
 */
function setupApiFetchMock(searchResponse: SearchResponse) {
  mockedApiFetch.mockImplementation((path: string) => {
    if (path.startsWith("/api/v1/regions")) {
      return Promise.resolve([]);
    }
    if (path.startsWith("/api/v1/search")) {
      return Promise.resolve(searchResponse);
    }
    return Promise.reject(new Error(`이 테스트에서 예상하지 못한 경로: ${path}`));
  });
}

/** SearchPage(서버 컴포넌트) 결과를 SearchLocationBar가 필요로 하는 Redux/Query 경계로 감싼다. */
function renderWithProviders(element: ReactElement) {
  const store = makeStore();
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <ReduxProvider store={store}>
      <QueryClientProvider client={queryClient}>{element}</QueryClientProvider>
    </ReduxProvider>,
  );
}

describe("SearchPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("drugId가 없으면 홈으로 유도하는 안내를 보여주고 API를 호출하지 않는다", async () => {
    render(await renderPage({}));

    expect(
      screen.getByText("검색 조건이 올바르지 않습니다"),
    ).toBeInTheDocument();
    expect(mockedApiFetch).not.toHaveBeenCalled();
  });

  it("drugId는 있는데 위치가 없으면 위치 설정 바와 안내를 보여주고 검색 API는 호출하지 않는다", async () => {
    setupApiFetchMock({ results: [] });
    renderWithProviders(await renderPage({ drugId: "1" }));

    expect(screen.getByText("위치를 설정해주세요")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "현재 위치 사용" }),
    ).toBeInTheDocument();
    const searchCalls = mockedApiFetch.mock.calls.filter(([path]) =>
      String(path).startsWith("/api/v1/search"),
    );
    expect(searchCalls).toHaveLength(0);
  });

  it("결과가 있으면 요약·카드·dataSource 고지를 보여주고 올바른 쿼리로 호출한다", async () => {
    const response: SearchResponse = {
      drug: { id: 1, displayName: "타이레놀 500mg" },
      summary: { resultCount: 1, maxSaving: 1300 },
      dataSource: "SEED",
      results: [
        {
          rank: 1,
          recommended: true,
          pharmacy: {
            id: 101,
            name: "가온약국",
            addressRoad: "서울특별시 강남구 테헤란로 123",
          },
          price: {
            repPrice: 2600,
            minPrice: 2500,
            reportCount: 4,
            lastReportedAt: "2026-09-10",
          },
          distanceM: 340,
          badges: ["LOWEST_PRICE"],
        },
      ],
    };
    setupApiFetchMock(response);

    renderWithProviders(await renderPage(BASE_QUERY));

    const searchCalls = mockedApiFetch.mock.calls.filter(([path]) =>
      String(path).startsWith("/api/v1/search"),
    );
    const calledUrl = searchCalls[0][0] as string;
    expect(calledUrl).toContain("drugId=1");
    expect(calledUrl).toContain("lat=37.5");
    expect(calledUrl).toContain("lng=127.0");
    expect(calledUrl).toContain("radius=2000");
    expect(calledUrl).toContain("sort=SCORE");

    expect(screen.getByText("타이레놀 500mg")).toBeInTheDocument();
    expect(screen.getByText(/최대 1,300원 절약 가능/)).toBeInTheDocument();
    expect(screen.getByText("가온약국")).toBeInTheDocument();
    expect(screen.getByText(/학습용 예시 데이터/)).toBeInTheDocument();
  });

  it("0건이고 suggestion이 있으면 반경 확대 링크를 보여준다", async () => {
    const response: SearchResponse = {
      drug: { id: 1, displayName: "타이레놀 500mg" },
      summary: { resultCount: 0 },
      dataSource: "SEED",
      results: [],
      suggestion: { type: "EXPAND_RADIUS", recommendedRadius: 5000, estimatedCount: 12 },
    };
    setupApiFetchMock(response);

    renderWithProviders(await renderPage(BASE_QUERY));

    const link = screen.getByRole("link", { name: "반경 넓혀서 다시 검색" });
    expect(link.getAttribute("href")).toContain("radius=5000");
  });

  it("radius/sort가 허용값 밖이면 기본값(2000/SCORE)으로 보정해 호출한다", async () => {
    setupApiFetchMock({ results: [], summary: { resultCount: 0 } });

    renderWithProviders(
      await renderPage({
        drugId: "1",
        lat: "37.5",
        lng: "127",
        radius: "9999",
        sort: "BOGUS",
      }),
    );

    const searchCalls = mockedApiFetch.mock.calls.filter(([path]) =>
      String(path).startsWith("/api/v1/search"),
    );
    const calledUrl = searchCalls[0][0] as string;
    expect(calledUrl).toContain("radius=2000");
    expect(calledUrl).toContain("sort=SCORE");
  });
});
