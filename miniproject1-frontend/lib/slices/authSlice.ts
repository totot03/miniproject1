import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

/**
 * 인증 세션 상태.
 *
 * 헤더의 로그인 표시, 제보 버튼 노출 여부 등 여러 화면이 함께 보는
 * 클라이언트 상태이므로 Redux가 맡는다 (docs/PRD.md §4.2).
 *
 * refreshToken은 여기에 두지 않는다. docs/API.md §1.4에 따라 Next.js
 * Route Handler를 경유해 httpOnly 쿠키로 보관한다.
 *
 * 로그인·토큰 갱신·lib/api.ts의 공급자 연결은 T-25에서 채운다.
 */

/** docs/DATABASE.md UserRole */
export type UserRole = "USER" | "ADMIN";

export interface AuthUser {
  id: number;
  email: string;
  nickname: string;
  role: UserRole;
}

export interface AuthState {
  user: AuthUser | null;
  /** 메모리에만 두는 access 토큰 (30분). localStorage에 두지 않는다 */
  accessToken: string | null;
  /** 최초 세션 복원이 끝났는지. false인 동안 로그인 여부를 판단하지 않는다 */
  initialized: boolean;
}

const initialState: AuthState = {
  user: null,
  accessToken: null,
  initialized: false,
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    /** 로그인 성공 또는 토큰 갱신 결과를 반영한다 (T-25) */
    setSession(
      state,
      action: PayloadAction<{ user: AuthUser; accessToken: string }>,
    ) {
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.initialized = true;
    },
  },
});

export const { setSession } = authSlice.actions;
export default authSlice.reducer;
