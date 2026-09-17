// @vitest-environment jsdom
import "@testing-library/jest-dom/vitest";

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider as ReduxProvider } from "react-redux";
import { afterEach, describe, expect, it, vi } from "vitest";

import { LoginForm } from "@/components/auth/LoginForm";
import { apiFetch, ApiError } from "@/lib/api";
import { makeStore } from "@/lib/store";

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

// login 썽크가 부르는 백엔드 호출만 목킹한다. 세션 저장(POST /api/auth/session)은
// 같은 오리진 plain fetch이므로 global fetch를 스텁한다.
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return { ...actual, apiFetch: vi.fn() };
});

const mockedApiFetch = vi.mocked(apiFetch);

function renderLoginForm(next = "/") {
  const store = makeStore();
  return render(
    <ReduxProvider store={store}>
      <LoginForm next={next} />
    </ReduxProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  mockedApiFetch.mockReset();
  pushMock.mockReset();
});

describe("LoginForm", () => {
  it("빈 채로 제출하면 클라이언트 검증 에러를 보여주고 API를 부르지 않는다", async () => {
    const user = userEvent.setup();
    renderLoginForm();

    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(await screen.findByText("올바른 이메일 형식이 아닙니다.")).toBeInTheDocument();
    expect(screen.getByText("비밀번호를 입력해 주세요.")).toBeInTheDocument();
    expect(mockedApiFetch).not.toHaveBeenCalled();
  });

  it("로그인에 성공하면 next 경로로 이동한다", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      accessToken: "access-1",
      refreshToken: "refresh-1",
      expiresIn: 1800,
      user: { id: 1, nickname: "민지", role: "USER" },
    });
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>(async () => ({ ok: true }) as Response),
    );

    const user = userEvent.setup();
    renderLoginForm("/reports/new");

    await user.type(screen.getByLabelText("이메일"), "minji@example.com");
    await user.type(screen.getByLabelText("비밀번호"), "Password123");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(pushMock).toHaveBeenCalledWith("/reports/new");
  });

  it("이메일·비밀번호가 틀리면 공통 에러 메시지를 보여주고 이동하지 않는다", async () => {
    mockedApiFetch.mockRejectedValueOnce(
      new ApiError(401, "UNAUTHENTICATED", "이메일 또는 비밀번호가 올바르지 않습니다."),
    );

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText("이메일"), "minji@example.com");
    await user.type(screen.getByLabelText("비밀번호"), "wrong-password");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(
      await screen.findByText("이메일 또는 비밀번호가 올바르지 않습니다."),
    ).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();
  });
});
