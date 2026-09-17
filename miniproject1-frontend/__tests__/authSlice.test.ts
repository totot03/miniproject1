import { configureStore } from "@reduxjs/toolkit";
import { afterEach, describe, expect, it, vi } from "vitest";

// apiFetch만 목으로 바꾸고 ApiError 등 나머지는 실제 구현을 쓴다.
// authSlice의 toAuthThunkError가 `error instanceof ApiError`로 분기하므로
// 실제 클래스가 필요하다.
vi.mock("@/lib/api", async () => {
  const actual = await vi.importActual<typeof import("@/lib/api")>("@/lib/api");
  return { ...actual, apiFetch: vi.fn() };
});

import { apiFetch, ApiError } from "@/lib/api";
import authReducer, {
  clearSession,
  login,
  logout,
  restoreSession,
  setSession,
  type AuthState,
} from "@/lib/slices/authSlice";

const mockedApiFetch = vi.mocked(apiFetch);

function makeTestStore() {
  return configureStore({ reducer: { auth: authReducer } });
}

/** app/api/auth/session/route.ts를 흉내내는 plain fetch 응답 스텁 */
function stubFetch(options: { ok: boolean; jsonBody?: unknown }) {
  const fetchMock = vi.fn<typeof fetch>(
    async () =>
      ({
        ok: options.ok,
        json: async () => options.jsonBody,
      }) as Response,
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

const SESSION_USER = { id: 42, nickname: "민지", role: "USER" as const };

afterEach(() => {
  vi.unstubAllGlobals();
  mockedApiFetch.mockReset();
});

describe("authSlice 리듀서", () => {
  const initialState: AuthState = { user: null, accessToken: null, status: "idle" };

  it("setSession은 로그인 세션을 반영하고 status를 authenticated로 바꾼다", () => {
    const state = authReducer(
      initialState,
      setSession({ user: SESSION_USER, accessToken: "token-1" }),
    );

    expect(state).toEqual({
      user: SESSION_USER,
      accessToken: "token-1",
      status: "authenticated",
    });
  });

  it("clearSession은 세션을 비우고 status를 unauthenticated로 바꾼다", () => {
    const loggedIn = authReducer(
      initialState,
      setSession({ user: SESSION_USER, accessToken: "token-1" }),
    );

    expect(authReducer(loggedIn, clearSession())).toEqual({
      user: null,
      accessToken: null,
      status: "unauthenticated",
    });
  });
});

describe("login 썽크", () => {
  it("백엔드 로그인 성공 + 세션 저장 성공이면 authenticated 상태가 된다", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      accessToken: "access-1",
      refreshToken: "refresh-1",
      expiresIn: 1800,
      user: SESSION_USER,
    });
    stubFetch({ ok: true });

    const store = makeTestStore();
    await store.dispatch(login({ email: "minji@example.com", password: "Password123" }));

    expect(store.getState().auth).toEqual({
      user: SESSION_USER,
      accessToken: "access-1",
      status: "authenticated",
    });
  });

  it("이메일·비밀번호가 틀리면 unauthenticated로 남고 에러 메시지를 담는다", async () => {
    mockedApiFetch.mockRejectedValueOnce(
      new ApiError(401, "UNAUTHENTICATED", "이메일 또는 비밀번호가 올바르지 않습니다."),
    );

    const store = makeTestStore();
    const result = await store.dispatch(
      login({ email: "minji@example.com", password: "wrong" }),
    );

    expect(login.rejected.match(result)).toBe(true);
    expect(result.payload).toEqual({
      code: "UNAUTHENTICATED",
      message: "이메일 또는 비밀번호가 올바르지 않습니다.",
      fieldErrors: undefined,
    });
    expect(store.getState().auth.status).toBe("unauthenticated");
    expect(store.getState().auth.user).toBeNull();
  });

  it("세션 저장(Route Handler) 호출이 실패하면 로그인 자체를 실패로 처리한다", async () => {
    mockedApiFetch.mockResolvedValueOnce({
      accessToken: "access-1",
      refreshToken: "refresh-1",
      expiresIn: 1800,
      user: SESSION_USER,
    });
    stubFetch({ ok: false });

    const store = makeTestStore();
    const result = await store.dispatch(
      login({ email: "minji@example.com", password: "Password123" }),
    );

    expect(login.rejected.match(result)).toBe(true);
    expect(store.getState().auth.status).toBe("unauthenticated");
  });
});

describe("restoreSession 썽크", () => {
  it("세션 쿠키가 유효하면 authenticated로 복원한다", async () => {
    stubFetch({
      ok: true,
      jsonBody: { accessToken: "access-2", expiresIn: 1800, user: SESSION_USER },
    });

    const store = makeTestStore();
    await store.dispatch(restoreSession());

    expect(store.getState().auth).toEqual({
      user: SESSION_USER,
      accessToken: "access-2",
      status: "authenticated",
    });
  });

  it("세션 쿠키가 없거나 만료됐으면 에러 없이 unauthenticated로 확정한다", async () => {
    stubFetch({ ok: false });

    const store = makeTestStore();
    const result = await store.dispatch(restoreSession());

    expect(restoreSession.fulfilled.match(result)).toBe(true);
    expect(result.payload).toBeNull();
    expect(store.getState().auth.status).toBe("unauthenticated");
  });

  it("동시에 두 번 호출돼도 fetch는 한 번만 나간다 (rotation 경쟁 방지)", async () => {
    // 실제로 재현된 버그: React Strict Mode가 마운트 effect를 두 번 실행해
    // restoreSession()이 거의 동시에 두 번 디스패치되면, 회전된 새 토큰을
    // 모르는 두 번째 요청이 이미 revoke된 옛 토큰으로 401을 받아 방금 저장된
    // 쿠키를 지워버렸다 — 로그인 직후 새로고침하면 로그아웃된 것처럼 보였다.
    const fetchMock = vi.fn<typeof fetch>(
      async () =>
        ({
          ok: true,
          json: async () => ({
            accessToken: "access-rotated",
            expiresIn: 1800,
            user: SESSION_USER,
          }),
        }) as Response,
    );
    vi.stubGlobal("fetch", fetchMock);

    const store = makeTestStore();
    const [first, second] = await Promise.all([
      store.dispatch(restoreSession()),
      store.dispatch(restoreSession()),
    ]);

    expect(fetchMock).toHaveBeenCalledOnce();
    expect(first.payload).toEqual(second.payload);
    expect(store.getState().auth.status).toBe("authenticated");
  });
});

describe("logout 썽크", () => {
  it("백엔드 호출이 실패해도(멱등) 스토어는 항상 비운다", async () => {
    const fetchMock = vi.fn<typeof fetch>(async () => {
      throw new Error("network error");
    });
    vi.stubGlobal("fetch", fetchMock);

    const preloadedState: { auth: AuthState } = {
      auth: { user: SESSION_USER, accessToken: "access-1", status: "authenticated" },
    };
    const store = configureStore({
      reducer: { auth: authReducer },
      preloadedState,
    });

    await store.dispatch(logout());

    expect(store.getState().auth).toEqual({
      user: null,
      accessToken: null,
      status: "unauthenticated",
    });
  });
});
