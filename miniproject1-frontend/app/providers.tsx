"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { Provider as ReduxProvider } from "react-redux";

import { setAccessTokenProvider, setRefreshHandler } from "@/lib/api";
import { restoreSession } from "@/lib/slices/authSlice";
import { makeStore } from "@/lib/store";

/**
 * 클라이언트 상태 도구 두 개를 세운다.
 *
 * - TanStack Query: 클라이언트에서 다시 불러오는 서버 데이터
 * - Redux Toolkit: 화면 간 공유되는 클라이언트 상태
 *
 * 경계 기준은 docs/PRD.md §4.2 참고. 검색 결과는 서버 컴포넌트 SSR +
 * URL 쿼리가 담당하므로 둘 중 어느 쪽에도 올리지 않는다.
 */
export function Providers({ children }: { children: ReactNode }) {
  // 스토어와 QueryClient를 모듈 스코프에서 만들면 서버 프로세스가
  // 모든 요청에 같은 인스턴스를 쓴다. 사용자 A의 캐시가 B에게 보이는
  // 문제가 되므로 반드시 마운트 단위로 만든다.
  //
  // Redux 공식 문서는 useRef를 안내하지만, React Compiler 규칙
  // (react-hooks/refs)이 렌더 중 ref.current 읽기를 금지한다.
  // useState 초기화 함수는 마운트당 한 번만 실행되므로 의미가 같고
  // 규칙에도 맞는다.
  const [store] = useState(makeStore);

  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60_000,
            retry: 1,
          },
        },
      }),
  );

  // lib/api.ts는 서버 컴포넌트에서도 import되어 store를 직접 참조할 수
  // 없으므로, 이 클라이언트 경계에서만 두 주입 지점을 연결한다. 이어서
  // 세션을 한 번 복원해 새로고침 후에도 로그인 상태가 이어지게 한다
  // (docs/ROADMAP.md T-25).
  useEffect(() => {
    setAccessTokenProvider(() => store.getState().auth.accessToken);
    setRefreshHandler(async () => {
      const result = await store.dispatch(restoreSession());
      if (restoreSession.fulfilled.match(result) && result.payload) {
        return result.payload.accessToken;
      }
      return null;
    });

    store.dispatch(restoreSession());

    return () => {
      setAccessTokenProvider(() => null);
      setRefreshHandler(null);
    };
  }, [store]);

  return (
    <ReduxProvider store={store}>
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    </ReduxProvider>
  );
}
