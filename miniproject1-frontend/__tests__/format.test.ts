import { describe, expect, it } from "vitest";

import {
  daysBetween,
  formatDistance,
  formatNumber,
  formatPrice,
  formatRelativeDate,
  todayInKST,
} from "@/lib/format";

describe("formatPrice", () => {
  it("천단위 콤마와 원 단위를 붙인다", () => {
    expect(formatPrice(2800)).toBe("2,800원");
    expect(formatPrice(200000)).toBe("200,000원");
  });

  it("1000원 미만은 콤마 없이 표기한다", () => {
    expect(formatPrice(900)).toBe("900원");
  });

  it("소수는 반올림한다", () => {
    expect(formatPrice(2799.6)).toBe("2,800원");
  });
});

describe("formatNumber", () => {
  it("단위 없이 숫자만 포맷한다", () => {
    expect(formatNumber(2800)).toBe("2,800");
  });
});

describe("formatDistance", () => {
  it("1000m 미만은 미터로 표기한다", () => {
    expect(formatDistance(340)).toBe("340m");
    expect(formatDistance(999)).toBe("999m");
  });

  it("1000m 이상은 킬로미터로 표기한다", () => {
    // 경계값. 1000은 km 쪽이다
    expect(formatDistance(1000)).toBe("1.0km");
    expect(formatDistance(1200)).toBe("1.2km");
    expect(formatDistance(5000)).toBe("5.0km");
  });

  it("음수나 NaN은 하이픈으로 표기한다", () => {
    expect(formatDistance(-1)).toBe("-");
    expect(formatDistance(Number.NaN)).toBe("-");
  });
});

describe("todayInKST", () => {
  it("UTC 자정 직후에도 KST 날짜는 이미 다음 날이다", () => {
    // 2026-09-15T00:30:00Z = KST 2026-09-15 09:30
    expect(todayInKST(new Date("2026-09-15T00:30:00Z"))).toBe("2026-09-15");
  });

  it("UTC 기준 전날 오후는 KST로 이미 다음 날이다", () => {
    // 2026-09-14T15:30:00Z = KST 2026-09-15 00:30
    // 로컬 시간으로 계산하면 09-14로 잡혀 하루가 어긋난다
    expect(todayInKST(new Date("2026-09-14T15:30:00Z"))).toBe("2026-09-15");
  });
});

describe("daysBetween", () => {
  it("과거 날짜는 양수 일수를 돌려준다", () => {
    expect(daysBetween("2026-09-12", "2026-09-15")).toBe(3);
  });

  it("월 경계를 넘어도 정확히 계산한다", () => {
    expect(daysBetween("2026-08-31", "2026-09-01")).toBe(1);
  });

  it("90일·180일 창 경계를 정확히 계산한다", () => {
    // 대표가격 산출 창(docs/PRD.md §F3.1)과 같은 기준이어야 한다
    expect(daysBetween("2026-06-17", "2026-09-15")).toBe(90);
  });

  it("형식이 맞지 않으면 null을 돌려준다", () => {
    expect(daysBetween("2026/09/12", "2026-09-15")).toBeNull();
  });
});

describe("formatRelativeDate", () => {
  const today = "2026-09-15";

  it("같은 날은 오늘이다", () => {
    expect(formatRelativeDate("2026-09-15", today)).toBe("오늘");
  });

  it("하루 전은 어제다", () => {
    expect(formatRelativeDate("2026-09-14", today)).toBe("어제");
  });

  it("이틀 이상은 N일 전이다", () => {
    expect(formatRelativeDate("2026-09-12", today)).toBe("3일 전");
    // STALE_DATA 뱃지 경계(30일)와 같은 기준
    expect(formatRelativeDate("2026-08-16", today)).toBe("30일 전");
    expect(formatRelativeDate("2026-06-20", today)).toBe("87일 전");
  });

  it("미래 날짜는 오늘로 취급한다", () => {
    expect(formatRelativeDate("2026-09-20", today)).toBe("오늘");
  });

  it("형식이 맞지 않으면 하이픈을 돌려준다", () => {
    expect(formatRelativeDate("알 수 없음", today)).toBe("-");
  });
});
