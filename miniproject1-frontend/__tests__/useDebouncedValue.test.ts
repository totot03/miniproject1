// @vitest-environment jsdom
import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useDebouncedValue } from "@/hooks/useDebouncedValue";

describe("useDebouncedValue", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("마운트 시 초기값을 즉시 반환한다", () => {
    const { result } = renderHook(() => useDebouncedValue("초기값", 300));
    expect(result.current).toBe("초기값");
  });

  it("delayMs가 지나기 전에는 이전 값을 유지한다", () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebouncedValue(value, 300),
      { initialProps: { value: "가" } },
    );

    rerender({ value: "가나" });
    act(() => {
      vi.advanceTimersByTime(299);
    });

    expect(result.current).toBe("가");
  });

  it("delayMs가 지나면 최신 값으로 갱신된다", () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebouncedValue(value, 300),
      { initialProps: { value: "가" } },
    );

    rerender({ value: "가나" });
    act(() => {
      vi.advanceTimersByTime(300);
    });

    expect(result.current).toBe("가나");
  });

  it("delayMs 안에 값이 연속으로 바뀌면 중간값은 버리고 마지막 값만 반영한다", () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebouncedValue(value, 300),
      { initialProps: { value: "가" } },
    );

    // "가나"로 바꾼 지 200ms 뒤(아직 300ms 안 됨) "가나다"로 또 바뀐 상황.
    // 타이핑 중 계속 바뀌는 검색어를 흉내낸다.
    rerender({ value: "가나" });
    act(() => {
      vi.advanceTimersByTime(200);
    });
    rerender({ value: "가나다" });
    act(() => {
      vi.advanceTimersByTime(200);
    });
    // "가나다"로 바뀐 지 200ms만 지났으므로 아직 타이머가 안 끝났다.
    // "가나"는 한 번도 반영되지 않고 그대로 버려져야 한다.
    expect(result.current).toBe("가");

    act(() => {
      vi.advanceTimersByTime(100);
    });
    expect(result.current).toBe("가나다");
  });
});
