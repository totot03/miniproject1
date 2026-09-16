"use client";

import { useEffect, useState } from "react";

/**
 * 값이 바뀐 뒤 delayMs 동안 추가 변경이 없을 때만 그 값을 반영한다.
 *
 * DrugAutocomplete의 검색어 300ms 디바운스(docs/ROADMAP.md T-17 2번)에 쓴다.
 * 입력마다 API를 부르면 서버가 시끄럽고, 같은 키워드를 다시 치면 TanStack
 * Query 캐시가 받아준다 — 그러려면 queryKey에 들어가는 값 자체가 "타이핑
 * 중"이 아니라 "타이핑이 잠시 멈춘" 시점의 값이어야 하므로, 컴포넌트 로직과
 * 분리한 이 훅이 그 경계를 담당한다.
 *
 * value/delayMs가 계속 바뀌는 동안은 매 렌더마다 이전 타이머를 정리하고
 * 새로 거는 것뿐이라, 마지막 변경 이후 delayMs가 지난 값만 최종적으로
 * 반영된다(중간값은 전부 버려짐 — 디바운스의 핵심 동작).
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
