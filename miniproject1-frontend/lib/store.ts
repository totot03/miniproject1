import { configureStore } from "@reduxjs/toolkit";

import authReducer from "@/lib/slices/authSlice";
import locationReducer from "@/lib/slices/locationSlice";
import reportDraftReducer from "@/lib/slices/reportDraftSlice";

/**
 * Redux 스토어 팩토리.
 *
 * 모듈 스코프에서 스토어를 한 번만 만들면, Next.js 서버가 프로세스 하나로
 * 모든 요청을 처리하기 때문에 요청 간 상태가 섞인다. 인증 세션이 다른
 * 사용자에게 노출될 수 있다는 뜻이다. 그래서 팩토리로 두고
 * app/providers.tsx에서 요청(마운트)마다 새로 만든다.
 */
export const makeStore = () =>
  configureStore({
    reducer: {
      location: locationReducer, // T-16
      auth: authReducer, // T-25
      reportDraft: reportDraftReducer, // T-29
    },
  });

export type AppStore = ReturnType<typeof makeStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];
