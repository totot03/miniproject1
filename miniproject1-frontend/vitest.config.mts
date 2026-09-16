import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    // tsconfig.json의 "@/*" 경로 별칭을 테스트에서도 그대로 쓴다.
    // Vite 8부터 네이티브 지원이므로 vite-tsconfig-paths 플러그인은 쓰지 않는다.
    tsconfigPaths: true,
  },
  test: {
    // 전역 환경은 node로 둔다 — 유틸 함수 테스트(.test.ts)는 DOM이 필요 없어
    // 이렇게 두는 쪽이 더 빠르다. 컴포넌트 렌더 테스트(.test.tsx)는 jsdom이
    // 필요한데, 전역을 jsdom으로 바꾸면 유틸 테스트까지 느려지므로 대신
    // 해당 파일 맨 위에 `// @vitest-environment jsdom` docblock을 적어
    // 파일 단위로만 override한다(Vitest 표준 opt-in 방식, T-17).
    environment: "node",
    include: ["__tests__/**/*.test.ts", "__tests__/**/*.test.tsx"],
  },
});
