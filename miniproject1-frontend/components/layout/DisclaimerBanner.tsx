import { TriangleAlert } from "lucide-react";

/**
 * 학습용 예시 데이터 고지 배너.
 *
 * docs/PRD.md §9의 필수 요구사항이다. 사용자가 시드 가격을 실제 판매가로
 * 오인해 약국을 방문하는 것을 막기 위한 것이므로, 특정 화면이 아니라
 * 루트 레이아웃에 두어 모든 페이지에 노출되게 한다.
 *
 * sticky로 두어 검색 결과를 스크롤하는 동안에도 계속 보인다.
 */
export function DisclaimerBanner() {
  return (
    <div
      role="note"
      className="bg-stale/15 text-foreground border-stale/30 sticky top-0 z-40 border-b"
    >
      <p className="mx-auto flex max-w-5xl items-center gap-2 px-4 py-2 text-xs leading-relaxed sm:text-sm">
        <TriangleAlert
          aria-hidden="true"
          className="text-stale size-4 shrink-0"
        />
        <span>
          본 서비스의 가격은 <strong className="font-semibold">학습용 예시 데이터</strong>
          입니다. 실제 판매가가 아닙니다.
        </span>
      </p>
    </div>
  );
}
