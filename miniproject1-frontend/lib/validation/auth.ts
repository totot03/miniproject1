import { z } from "zod";

/**
 * 로그인·회원가입 폼이 공유하는 검증 규칙.
 *
 * docs/API.md §2 서버 검증 규칙과 동일하게 맞춘다 — 클라이언트에서 먼저
 * 걸러줘야 불필요한 요청·서버 왕복이 줄어든다. 최종 판정은 항상 서버가
 * 한다는 점은 변하지 않는다.
 */

export const emailSchema = z.email({ error: "올바른 이메일 형식이 아닙니다." }).max(255);

export const passwordSchema = z
  .string()
  .min(8, { error: "비밀번호는 8자 이상이어야 합니다." })
  .max(64, { error: "비밀번호는 64자 이하여야 합니다." })
  .regex(/[A-Za-z]/, { error: "영문을 포함해야 합니다." })
  .regex(/[0-9]/, { error: "숫자를 포함해야 합니다." });

export const nicknameSchema = z
  .string()
  .min(2, { error: "닉네임은 2자 이상이어야 합니다." })
  .max(30, { error: "닉네임은 30자 이하여야 합니다." });

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, { error: "비밀번호를 입력해 주세요." }),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

export const signupSchema = z.object({
  email: emailSchema,
  password: passwordSchema,
  nickname: nicknameSchema,
});

export type SignupFormValues = z.infer<typeof signupSchema>;
