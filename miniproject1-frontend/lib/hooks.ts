"use client";

import { useDispatch, useSelector } from "react-redux";

import type { AppDispatch, RootState } from "@/lib/store";

/**
 * 타입이 지정된 Redux 훅.
 *
 * 컴포넌트에서 useDispatch / useSelector를 직접 부르지 말고 이 둘을 쓴다.
 * 그래야 state 타입과 thunk 디스패치 타입이 자동으로 붙는다.
 *
 * 주의: 서버 컴포넌트에서 부르면 터진다. app/providers.tsx 아래의
 * "use client" 컴포넌트에서만 쓴다 (docs/PRD.md §4.2).
 */
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
