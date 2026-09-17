// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import SearchError from "@/app/search/error";
import SearchLoading from "@/app/search/loading";
import { ApiError } from "@/lib/api";

describe("SearchLoading", () => {
  afterEach(cleanup);

  it("로딩 중임을 알리는 스켈레톤을 보여준다", () => {
    render(<SearchLoading />);

    expect(screen.getByRole("status", { name: "불러오는 중" })).toBeInTheDocument();
  });
});

describe("SearchError", () => {
  afterEach(cleanup);

  it("ApiError 메시지와 traceId를 보여주고 retry를 다시 시도 버튼에 연결한다", async () => {
    const user = userEvent.setup();
    const retry = vi.fn();
    const error = new ApiError(
      400,
      "VALIDATION_FAILED",
      "위치 정보가 필요합니다.",
      undefined,
      "9f3a1c2e",
    );

    render(<SearchError error={error} retry={retry} />);

    expect(screen.getByText("위치 정보가 필요합니다.")).toBeInTheDocument();
    expect(screen.getByText(/traceId: 9f3a1c2e/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /다시 시도/ }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it("홈으로 돌아가는 링크를 제공한다", () => {
    render(
      <SearchError error={new Error("알 수 없는 오류")} retry={vi.fn()} />,
    );

    expect(screen.getByRole("link", { name: "홈으로 돌아가기" })).toHaveAttribute(
      "href",
      "/",
    );
  });
});
