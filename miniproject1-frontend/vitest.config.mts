import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    // tsconfig.json의 "@/*" 경로 별칭을 테스트에서도 그대로 쓴다.
    // Vite 8부터 네이티브 지원이므로 vite-tsconfig-paths 플러그인은 쓰지 않는다.
    tsconfigPaths: true,
  },
  test: {
    // 유틸 함수만 검증하므로 jsdom이 필요 없다.
    // 컴포넌트 렌더 테스트가 필요해지면 jsdom + Testing Library를 추가한다.
    environment: "node",
    include: ["__tests__/**/*.test.ts"],
  },
});
