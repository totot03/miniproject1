import { ApiError } from "@/lib/api";

/** error 종류를 모를 때 쓰는 최종 폴백 문구 */
const DEFAULT_FALLBACK = "요청을 처리하지 못했습니다.";

/**
 * 어떤 예외가 와도 화면에 보여줄 한글 문구를 돌려준다.
 *
 * ApiError는 항상 한글 메시지를 갖고 있으므로(lib/api.ts — 백엔드가 내려주는
 * 문구 또는 상태코드별 FALLBACK_MESSAGES) 그대로 쓴다. 그 외(예: 컴포넌트
 * 렌더링 중 던져진 일반 Error)는 원문이 영어일 수 있어 절대 그대로 보여주지
 * 않고 fallback으로 대체한다(docs/ROADMAP.md T-36 — 영어 원문 노출 금지).
 */
export function getErrorMessage(error: unknown, fallback: string = DEFAULT_FALLBACK): string {
  if (error instanceof ApiError) return error.message;
  return fallback;
}
