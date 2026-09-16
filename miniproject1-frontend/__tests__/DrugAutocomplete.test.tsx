// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider as ReduxProvider } from "react-redux";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DrugAutocomplete } from "@/components/DrugAutocomplete";
import { apiFetch } from "@/lib/api";
import { setCoordinates } from "@/lib/slices/locationSlice";
import { makeStore } from "@/lib/store";
import type { components } from "@/types/api";

type DrugPage = components["schemas"]["PageResponseDrugSummaryResponse"];

// next/navigation의 useRouter를 목킹한다. push 호출 여부/인자를 검증한다.
// vi.mock 팩토리는 파일 맨 위로 호이스팅되므로, 참조하는 변수는
// vi.hoisted로 함께 끌어올려야 한다(Vitest 표준 패턴).
const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

// lib/api의 apiFetch를 목킹한다 — 실제 네트워크 호출을 하지 않는다.
vi.mock("@/lib/api", () => ({
  apiFetch: vi.fn(),
}));

const mockedApiFetch = vi.mocked(apiFetch);

/** 타이레놀 500mg의 포장 단위가 다른 두 행(8정/16정) — 프로젝트 불변 규칙(포장 단위가 다르면 별개의 drug 행)을 그대로 보여주는 예시다. */
const TWO_DRUGS: DrugPage = {
  content: [
    { id: 1, displayName: "타이레놀 500mg", packageUnit: "8정" },
    { id: 2, displayName: "타이레놀 500mg", packageUnit: "16정" },
  ],
  page: 0,
  size: 8,
  totalElements: 2,
  totalPages: 1,
  hasNext: false,
};

const EMPTY_DRUGS: DrugPage = {
  content: [],
  page: 0,
  size: 8,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
};

let drugsResponse: DrugPage = TWO_DRUGS;

/**
 * RegionPicker(위치 거부 시 모달)가 DrugAutocomplete 안에 항상 함께
 * 렌더링되어 있고, Dialog가 닫혀 있어도 내부 useQuery(['regions'])는
 * 마운트 시점에 그대로 실행된다. 그래서 apiFetch 목은 경로로 분기해야
 * /api/v1/drugs 호출과 /api/v1/regions 호출을 섞지 않는다.
 */
function setupApiFetchMock() {
  mockedApiFetch.mockImplementation((path: string) => {
    if (path.startsWith("/api/v1/regions")) {
      return Promise.resolve([]);
    }
    if (path.startsWith("/api/v1/drugs")) {
      return Promise.resolve(drugsResponse);
    }
    return Promise.reject(new Error(`이 테스트에서 예상하지 못한 경로: ${path}`));
  });
}

function renderDrugAutocomplete({ hasLocation = false } = {}) {
  const store = makeStore();
  if (hasLocation) {
    store.dispatch(setCoordinates({ lat: 37.5665, lng: 126.978 }));
  }
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });

  render(
    <ReduxProvider store={store}>
      <QueryClientProvider client={queryClient}>
        <DrugAutocomplete />
      </QueryClientProvider>
    </ReduxProvider>,
  );
}

function getSearchInput() {
  return screen.getByRole("combobox", { name: "약품 검색" });
}

function wait(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

describe("DrugAutocomplete", () => {
  beforeEach(() => {
    drugsResponse = TWO_DRUGS;
    setupApiFetchMock();
    pushMock.mockClear();
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  // 실제 300ms 디바운스를 fake timer로 흉내내는 대신 real timer로 그냥
  // 기다린다 — TanStack Query의 프로미스 해석까지 fake timer로 맞추려면
  // vi.advanceTimersByTimeAsync 등을 정교하게 다뤄야 해서 오히려 더
  // 깨지기 쉽다. 테스트당 수백 ms 정도라 전체 실행 시간에 큰 영향은 없다.

  it("2자 미만을 입력하면 약품 검색 API를 호출하지 않는다", async () => {
    const user = userEvent.setup();
    renderDrugAutocomplete();

    await user.type(getSearchInput(), "타");
    await wait(400); // 디바운스(300ms)가 지나고도 호출되지 않아야 한다

    const drugCalls = mockedApiFetch.mock.calls.filter(([path]) =>
      String(path).startsWith("/api/v1/drugs"),
    );
    expect(drugCalls).toHaveLength(0);
  });

  it("2자 이상 입력하면 후보 목록에 약품명과 포장 단위가 함께 표시된다", async () => {
    const user = userEvent.setup();
    renderDrugAutocomplete();

    await user.type(getSearchInput(), "타이레놀");

    const options = await screen.findAllByRole("option");
    expect(options).toHaveLength(2);
    expect(options[0]).toHaveTextContent("타이레놀 500mg");
    expect(options[0]).toHaveTextContent("8정");
    expect(options[1]).toHaveTextContent("16정");
  });

  it("ArrowDown으로 이동하면 aria-activedescendant가 활성 옵션을 가리킨다", async () => {
    const user = userEvent.setup();
    renderDrugAutocomplete();

    const input = getSearchInput();
    await user.type(input, "타이레놀");
    const options = await screen.findAllByRole("option");
    expect(options).toHaveLength(2);

    await user.keyboard("{ArrowDown}");
    expect(input).toHaveAttribute("aria-activedescendant", options[0].id);

    await user.keyboard("{ArrowDown}");
    expect(input).toHaveAttribute("aria-activedescendant", options[1].id);
  });

  it("위치가 이미 확정된 상태에서 Enter로 선택하면 /search로 이동한다", async () => {
    const user = userEvent.setup();
    renderDrugAutocomplete({ hasLocation: true });

    const input = getSearchInput();
    await user.type(input, "타이레놀");
    await screen.findAllByRole("option");

    await user.keyboard("{ArrowDown}{Enter}");

    expect(pushMock).toHaveBeenCalledWith(
      "/search?drugId=1&lat=37.5665&lng=126.978&radius=2000",
    );
  });

  it("Escape를 누르면 목록만 닫히고 입력값은 유지된다", async () => {
    const user = userEvent.setup();
    renderDrugAutocomplete();

    const input = getSearchInput();
    await user.type(input, "타이레놀");
    await screen.findAllByRole("option");

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
    expect(input).toHaveValue("타이레놀");
  });

  it("검색 결과가 없으면 빈 상태 문구를 보여준다", async () => {
    drugsResponse = EMPTY_DRUGS;
    const user = userEvent.setup();
    renderDrugAutocomplete();

    await user.type(getSearchInput(), "존재하지않는약");

    expect(await screen.findByText("검색 결과가 없습니다")).toBeInTheDocument();
  });
});
