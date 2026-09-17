import { createAsyncThunk, createSlice, type PayloadAction } from "@reduxjs/toolkit";

import { apiFetch, ApiError, type ApiFieldError } from "@/lib/api";

/**
 * 인증 세션 상태.
 *
 * 헤더의 로그인 표시, 제보 버튼 노출 여부 등 여러 화면이 함께 보는
 * 클라이언트 상태이므로 Redux가 맡는다 (docs/PRD.md §4.2).
 *
 * refreshToken은 여기에 두지 않는다. docs/API.md §1.4에 따라
 * app/api/auth/session/route.ts(Route Handler)를 경유해 httpOnly 쿠키로
 * 보관하고, 브라우저 JS는 그 값을 한 번도 들고 있지 않는다.
 */

/** docs/DATABASE.md UserRole */
export type UserRole = "USER" | "ADMIN";

export interface AuthUser {
  id: number;
  /**
   * 로그인·세션 갱신 응답(docs/API.md §2)에는 email이 없다. GET /auth/me
   * (내 제보 화면, 향후 티켓)에서만 채워지므로 optional로 둔다.
   */
  email?: string;
  nickname: string;
  role: UserRole;
}

export type AuthStatus = "idle" | "loading" | "authenticated" | "unauthenticated";

export interface AuthState {
  user: AuthUser | null;
  /** 메모리에만 두는 access 토큰 (30분). localStorage에 두지 않는다 */
  accessToken: string | null;
  /**
   * idle: 앱이 막 마운트되어 세션 복원 전. loading: 복원·로그인 진행 중.
   * authenticated/unauthenticated: 복원이 끝나 로그인 여부가 확정됨.
   * 헤더는 idle/loading 동안 로그인 버튼과 닉네임 중 아무것도 단정해
   * 보여주지 않는다 (깜빡임 방지).
   */
  status: AuthStatus;
}

const initialState: AuthState = {
  user: null,
  accessToken: null,
  status: "idle",
};

/** ApiError를 직렬화 가능한 형태로 남긴다. 기본 rejected 액션은 이 정보를 버린다 */
export interface AuthThunkError {
  code: string;
  message: string;
  fieldErrors?: ApiFieldError[];
}

function toAuthThunkError(error: unknown): AuthThunkError {
  if (error instanceof ApiError) {
    return { code: error.code, message: error.message, fieldErrors: error.fieldErrors };
  }
  return { code: "INTERNAL_ERROR", message: "알 수 없는 오류가 발생했습니다." };
}

interface AuthSessionUser {
  id: number;
  nickname: string;
  role: UserRole;
}

interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: AuthSessionUser;
}

/** app/api/auth/session/route.ts GET 응답 (실패하면 이 함수 밖에서 null 처리) */
interface SessionResponse {
  accessToken: string;
  expiresIn: number;
  user: AuthSessionUser;
}

/**
 * 이메일·비밀번호로 로그인한다.
 *
 * 1) 백엔드에서 access+refresh 토큰을 받는다
 * 2) refreshToken은 즉시 Route Handler에 위임해 httpOnly 쿠키로 저장한다
 *    (여기서 넘기고 나면 이 함수는 refreshToken을 다시 참조하지 않는다)
 * 3) accessToken만 스토어에 남긴다
 */
export const login = createAsyncThunk<
  { user: AuthUser; accessToken: string },
  { email: string; password: string },
  { rejectValue: AuthThunkError }
>("auth/login", async ({ email, password }, { rejectWithValue }) => {
  try {
    const data = await apiFetch<LoginResponse>("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });

    const sessionRes = await fetch("/api/auth/session", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: data.refreshToken }),
    });
    if (!sessionRes.ok) {
      return rejectWithValue({
        code: "INTERNAL_ERROR",
        message: "세션 저장에 실패했습니다. 다시 시도해 주세요.",
      });
    }

    return { user: data.user, accessToken: data.accessToken };
  } catch (error) {
    return rejectWithValue(toAuthThunkError(error));
  }
});

type RestoreResult = { user: AuthUser; accessToken: string } | null;

/**
 * GET /api/auth/session은 호출할 때마다 refreshToken을 회전시킨다(rotation).
 * 그런데 이 함수는 앱 마운트 시(React 19 Strict Mode가 effect를 두 번
 * 실행한다)와 lib/api.ts 401 인터셉터가 동시에 부를 수 있다 — 두 요청이
 * 겹치면 먼저 응답한 쪽이 쿠키를 새 토큰으로 갈아끼우고, 뒤이어 도착한
 * 요청은 이미 폐기된(revoke된) 옛 토큰으로 호출돼 401을 받아 방금 막
 * 저장된 새 쿠키까지 지워버린다 — 로그인 직후 새로고침하면 로그아웃되는
 * 것처럼 보이는 버그로 실제로 재현됐다. 진행 중인 호출이 있으면 새로
 * fetch하지 않고 그 결과를 같이 기다려서 이 경쟁을 없앤다.
 */
let inFlightRestore: Promise<RestoreResult> | null = null;

/**
 * 쿠키의 refreshToken으로 세션을 복원(또는 갱신)한다.
 *
 * 앱 마운트 시 최초 1회, 그리고 lib/api.ts의 401 인터셉터가 재시도 직전에
 * 호출한다. 쿠키가 없거나 만료됐으면 에러가 아니라 "비로그인 상태"로
 * 취급한다 — 새 방문자에게도 항상 호출되는 정상 경로이기 때문이다.
 */
export const restoreSession = createAsyncThunk<RestoreResult>(
  "auth/restoreSession",
  async () => {
    if (inFlightRestore) return inFlightRestore;

    inFlightRestore = (async () => {
      try {
        const res = await fetch("/api/auth/session");
        if (!res.ok) return null;

        const data = (await res.json()) as SessionResponse;
        return { user: data.user, accessToken: data.accessToken };
      } finally {
        inFlightRestore = null;
      }
    })();

    return inFlightRestore;
  },
);

/** 로그아웃. 백엔드 호출이 실패해도(이미 로그아웃 등) 스토어는 항상 비운다 */
export const logout = createAsyncThunk("auth/logout", async () => {
  await fetch("/api/auth/session", { method: "DELETE" }).catch(() => {});
});

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    /** 401 인터셉터 등에서 세션 갱신 결과를 즉시 반영할 때 쓴다 */
    setSession(
      state,
      action: PayloadAction<{ user: AuthUser; accessToken: string }>,
    ) {
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.status = "authenticated";
    },
    clearSession(state) {
      state.user = null;
      state.accessToken = null;
      state.status = "unauthenticated";
    },
  },
  extraReducers(builder) {
    builder
      .addCase(login.pending, (state) => {
        state.status = "loading";
      })
      .addCase(login.fulfilled, (state, action) => {
        state.user = action.payload.user;
        state.accessToken = action.payload.accessToken;
        state.status = "authenticated";
      })
      .addCase(login.rejected, (state) => {
        state.user = null;
        state.accessToken = null;
        state.status = "unauthenticated";
      })
      .addCase(restoreSession.pending, (state) => {
        state.status = "loading";
      })
      .addCase(restoreSession.fulfilled, (state, action) => {
        if (action.payload) {
          state.user = action.payload.user;
          state.accessToken = action.payload.accessToken;
          state.status = "authenticated";
        } else {
          state.user = null;
          state.accessToken = null;
          state.status = "unauthenticated";
        }
      })
      .addCase(restoreSession.rejected, (state) => {
        state.user = null;
        state.accessToken = null;
        state.status = "unauthenticated";
      })
      .addCase(logout.fulfilled, (state) => {
        state.user = null;
        state.accessToken = null;
        state.status = "unauthenticated";
      })
      .addCase(logout.rejected, (state) => {
        state.user = null;
        state.accessToken = null;
        state.status = "unauthenticated";
      });
  },
});

export const { setSession, clearSession } = authSlice.actions;
export default authSlice.reducer;
