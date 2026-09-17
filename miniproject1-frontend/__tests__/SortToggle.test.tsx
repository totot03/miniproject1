// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { SortToggle } from "@/components/SortToggle";

// next/navigation을 목킹한다(DrugAutocomplete.test.tsx와 같은 방식).
// vi.mock 팩토리는 호이스팅되므로 참조 변수는 vi.hoisted로 끌어올린다.
const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
  usePathname: () => "/search",
  useSearchParams: () =>
    new URLSearchParams("drugId=1&lat=37.5&lng=127&radius=2000&sort=SCORE"),
}));

describe("SortToggle", () => {
  afterEach(() => {
    cleanup();
    pushMock.mockClear();
  });

  it("현재 반경 버튼에만 aria-pressed=true가 붙는다", () => {
    render(<SortToggle sort="SCORE" radius={2000} />);

    expect(screen.getByRole("button", { name: "2km" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByRole("button", { name: "5km" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("반경 버튼 클릭 시 radius만 바꾸고 drugId/lat/lng/sort는 보존한다", async () => {
    const user = userEvent.setup();
    render(<SortToggle sort="SCORE" radius={2000} />);

    await user.click(screen.getByRole("button", { name: "5km" }));

    expect(pushMock).toHaveBeenCalledTimes(1);
    const pushedUrl = new URL(pushMock.mock.calls[0][0], "http://localhost");
    expect(pushedUrl.pathname).toBe("/search");
    expect(Object.fromEntries(pushedUrl.searchParams)).toEqual({
      drugId: "1",
      lat: "37.5",
      lng: "127",
      radius: "5000",
      sort: "SCORE",
    });
  });

  it("500m 반경을 클릭해도 다른 쿼리에는 영향이 없다", async () => {
    const user = userEvent.setup();
    render(<SortToggle sort="SCORE" radius={2000} />);

    await user.click(screen.getByRole("button", { name: "500m" }));

    const pushedUrl = new URL(pushMock.mock.calls[0][0], "http://localhost");
    expect(pushedUrl.searchParams.get("radius")).toBe("500");
    // 목킹된 useSearchParams가 실제 URL의 sort=SCORE를 돌려주므로 그대로 보존돼야 한다.
    expect(pushedUrl.searchParams.get("sort")).toBe("SCORE");
  });
});
