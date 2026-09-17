/**
 * 검색 결과 "1위" 판정 (docs/ROADMAP.md T-18/T-22).
 *
 * PharmacyResultCard(리스트)와 pharmacy-map(지도)이 같은 함수를 써야
 * 두 화면에서 1위 약국이 서로 다르게 보이는 불일치가 생기지 않는다.
 */
export interface TopPickInput {
  recommended?: boolean;
  rank?: number;
}

export function isTopPick(item: TopPickInput): boolean {
  return item.recommended === true || item.rank === 1;
}
