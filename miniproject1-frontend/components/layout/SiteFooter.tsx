/**
 * 전역 푸터.
 *
 * docs/PRD.md §9에 따라 고지 문구를 배너와 푸터 양쪽에 둔다.
 * 실명 약국 데이터에 생성된 가격이 붙어 있으므로 데모 목적임을 함께 밝힌다.
 */
export function SiteFooter() {
  return (
    <footer className="bg-muted/40 mt-auto border-t">
      <div className="text-muted-foreground mx-auto max-w-5xl space-y-2 px-4 py-6 text-xs leading-relaxed">
        <p>
          <strong className="text-foreground font-semibold">
            본 서비스의 가격은 학습용 예시 데이터입니다.
          </strong>{" "}
          실제 판매가가 아니며, 이 정보를 근거로 약국을 방문하지 마십시오.
        </p>
        <p>
          약국 정보는 공공데이터를 사용했으나 가격은 전부 생성된 값입니다. 학습
          목적의 비공개 데모이며 대외 공개·배포하지 않습니다.
        </p>
        <p>약값알림 · 미니 프로젝트</p>
      </div>
    </footer>
  );
}
