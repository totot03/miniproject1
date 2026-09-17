// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import SearchPage from "@/app/search/page";
import { apiFetch } from "@/lib/api";
import type { components } from "@/types/api";

type SearchResponse = components["schemas"]["SearchResponse"];

// SortToggle이 내부에서 next/navigation을 쓰므로 다른 테스트와 같은 방식으로 목킹한다.
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

describe("SearchPage", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("drugId나 위치가 없으면 홈으로 유도하는 안내를 보여주고 API를 호출하지 않는다", async () => {
    render(await renderPage({}));

    expect(
      screen.getByText("검색 조건이 올바르지 않습니다"),
    ).toBeInTheDocument();
    expect(mockedApiFetch).not.toHaveBeenCalled();
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
    mockedApiFetch.mockResolvedValueOnce(response);

    render(await renderPage(BASE_QUERY));

    const calledUrl = mockedApiFetch.mock.calls[0][0] as string;
    expect(calledUrl.startsWith("/api/v1/search?")).toBe(true);
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
    mockedApiFetch.mockResolvedValueOnce(response);

    render(await renderPage(BASE_QUERY));

    const link = screen.getByRole("link", { name: "반경 넓혀서 다시 검색" });
    expect(link.getAttribute("href")).toContain("radius=5000");
  });

  it("radius/sort가 허용값 밖이면 기본값(2000/SCORE)으로 보정해 호출한다", async () => {
    const response: SearchResponse = { results: [], summary: { resultCount: 0 } };
    mockedApiFetch.mockResolvedValueOnce(response);

    await renderPage({
      drugId: "1",
      lat: "37.5",
      lng: "127",
      radius: "9999",
      sort: "BOGUS",
    });

    const calledUrl = mockedApiFetch.mock.calls[0][0] as string;
    expect(calledUrl).toContain("radius=2000");
    expect(calledUrl).toContain("sort=SCORE");
  });
});
