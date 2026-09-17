// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { PharmacyResultCard } from "@/components/PharmacyResultCard";
import type { components } from "@/types/api";

type SearchResultItem = components["schemas"]["SearchResultItem"];

/** docs/API.md §5 응답 예시의 1위 항목(recommended, LOWEST_PRICE)을 그대로 옮긴 것. */
const TOP_PICK: SearchResultItem = {
  rank: 1,
  recommended: true,
  pharmacy: {
    id: 101,
    name: "가온약국",
    addressRoad: "서울특별시 강남구 테헤란로 123",
    lat: 37.5012,
    lng: 127.0396,
    phone: "02-555-1234",
  },
  price: {
    repPrice: 2600,
    minPrice: 2500,
    avgPrice: 2640,
    savingVsCandidateAvg: 540,
    reportCount: 4,
    lastReportedAt: "2026-09-10",
    daysSinceLastReport: 5,
  },
  distanceM: 340,
  score: 0.9124,
  badges: ["LOWEST_PRICE"],
};

/** docs/API.md §5 응답 예시의 2위 항목(LOW_CONFIDENCE, STALE_DATA)을 그대로 옮긴 것. */
const STALE_LOW_CONFIDENCE: SearchResultItem = {
  rank: 2,
  recommended: false,
  pharmacy: {
    id: 118,
    name: "새봄약국",
    addressRoad: "서울특별시 강남구 도곡로 45",
    lat: 37.4991,
    lng: 127.0301,
    phone: "02-555-5678",
  },
  price: {
    repPrice: 2900,
    minPrice: 2900,
    avgPrice: 2900,
    savingVsCandidateAvg: 240,
    reportCount: 1,
    lastReportedAt: "2026-06-20",
    daysSinceLastReport: 87,
  },
  distanceM: 180,
  score: 0.7208,
  badges: ["LOW_CONFIDENCE", "STALE_DATA"],
};

describe("PharmacyResultCard", () => {
  afterEach(cleanup);

  it("1위 카드는 강조 뱃지와 평균 대비 절감액 문구를 보여준다", () => {
    render(<PharmacyResultCard item={TOP_PICK} />);

    expect(screen.getByText("가온약국")).toBeInTheDocument();
    expect(screen.getByText("서울특별시 강남구 테헤란로 123")).toBeInTheDocument();
    expect(screen.getByText("최저가 추천")).toBeInTheDocument();
    expect(screen.getByText("평균보다 540원 저렴")).toBeInTheDocument();
    expect(screen.getByText("제보 4건")).toBeInTheDocument();
  });

  it("LOW_CONFIDENCE·STALE_DATA 뱃지를 문구로 변환해 보여준다", () => {
    render(<PharmacyResultCard item={STALE_LOW_CONFIDENCE} />);

    expect(screen.getByText("정보 부족")).toBeInTheDocument();
    expect(screen.getByText("오래된 정보")).toBeInTheDocument();
    // 1위가 아니므로 "최저가 추천" 뱃지는 없어야 한다.
    expect(screen.queryByText("최저가 추천")).not.toBeInTheDocument();
  });

  it("필수 필드가 없으면 크래시 없이 아무것도 렌더링하지 않는다", () => {
    const missingPrice: SearchResultItem = {
      rank: 3,
      pharmacy: { id: 200, name: "이름만 있는 약국", addressRoad: "주소" },
      distanceM: 500,
      // price가 통째로 없는 경우 — 서버 응답이 예상과 다를 때의 방어선.
    };

    const { container } = render(<PharmacyResultCard item={missingPrice} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("savingVsCandidateAvg가 없거나 0 이하이면 절감액 문구를 보여주지 않는다", () => {
    const noSaving: SearchResultItem = {
      ...TOP_PICK,
      price: { ...TOP_PICK.price, savingVsCandidateAvg: 0 },
    };

    render(<PharmacyResultCard item={noSaving} />);

    expect(screen.queryByText(/저렴$/)).not.toBeInTheDocument();
  });
});
