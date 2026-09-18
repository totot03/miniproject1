import { z } from "zod";

import { daysBetween, todayInKST } from "@/lib/format";

/**
 * 가격 제보 폼(T-29)의 클라이언트 검증 규칙.
 *
 * docs/API.md §6 `POST /api/v1/price-reports` 서버 검증과 동일하게 맞춘다.
 * 최종 판정은 항상 서버가 한다는 원칙은 lib/validation/auth.ts와 같다.
 */

/** docs/ROADMAP.md T-29 6번 */
export const MIN_PRICE = 100;
export const MAX_PRICE = 200_000;
/** docs/API.md §6 purchasedAt — 180일 초과 과거 불가 */
export const MAX_PAST_DAYS = 180;

export const priceSchema = z
  .number({ error: "가격을 입력해 주세요." })
  .int({ error: "가격은 숫자로만 입력해 주세요." })
  .min(MIN_PRICE, { error: `가격은 ${MIN_PRICE.toLocaleString("ko-KR")}원 이상이어야 합니다.` })
  .max(MAX_PRICE, { error: `가격은 ${MAX_PRICE.toLocaleString("ko-KR")}원 이하여야 합니다.` });

/** purchasedAt(YYYY-MM-DD)이 오늘로부터 며칠 전인지. 미래면 음수, 파싱 실패면 null */
function daysAgoFromToday(purchasedAt: string): number | null {
  return daysBetween(purchasedAt, todayInKST());
}

export const purchasedAtSchema = z
  .string()
  .min(1, { error: "구매일을 입력해 주세요." })
  .refine((value) => daysAgoFromToday(value) !== null, {
    error: "날짜 형식이 올바르지 않습니다.",
  })
  .refine((value) => (daysAgoFromToday(value) ?? -1) >= 0, {
    error: "미래 날짜는 입력할 수 없습니다.",
  })
  .refine((value) => (daysAgoFromToday(value) ?? Infinity) <= MAX_PAST_DAYS, {
    error: `구매일은 오늘부터 ${MAX_PAST_DAYS}일 이내여야 합니다.`,
  });

export const memoSchema = z
  .string()
  .max(200, { error: "메모는 200자 이하로 입력해 주세요." });

export const reportSchema = z.object({
  pharmacyId: z.number({ error: "약국을 선택해 주세요." }).int().positive(),
  pharmacyName: z.string().min(1, { error: "약국을 선택해 주세요." }),
  drugId: z.number({ error: "약품을 선택해 주세요." }).int().positive(),
  drugName: z.string().min(1, { error: "약품을 선택해 주세요." }),
  price: priceSchema,
  purchasedAt: purchasedAtSchema,
  memo: memoSchema.optional(),
  uploadedFileId: z.number().int().positive().nullable(),
});

export type ReportFormValues = z.infer<typeof reportSchema>;
