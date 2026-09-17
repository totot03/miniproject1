"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { apiFetch, ApiError } from "@/lib/api";
import { signupSchema, type SignupFormValues } from "@/lib/validation/auth";

interface SignupResponse {
  id: number;
  email: string;
  nickname: string;
  role: "USER" | "ADMIN";
  createdAt: string;
}

/**
 * 회원가입 폼.
 *
 * signup은 토큰을 돌려주지 않는다(docs/API.md §2) — 세션을 만들 필요가
 * 없으므로 authSlice 썽크 없이 apiFetch를 바로 호출하고, 완료되면
 * 로그인 화면으로 보낸다.
 */
export function SignupForm() {
  const router = useRouter();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { email: "", password: "", nickname: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await apiFetch<SignupResponse>("/api/v1/auth/signup", {
        method: "POST",
        body: JSON.stringify(values),
      });
      router.push("/login?signedUp=1");
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === "EMAIL_ALREADY_EXISTS") {
          setError("email", { message: "이미 가입된 이메일입니다." });
          return;
        }
        if (error.fieldErrors?.length) {
          for (const fieldError of error.fieldErrors) {
            if (
              fieldError.field === "email" ||
              fieldError.field === "password" ||
              fieldError.field === "nickname"
            ) {
              setError(fieldError.field, { message: fieldError.reason });
            }
          }
          return;
        }
        setError("root", { message: error.message });
        return;
      }
      setError("root", { message: "가입에 실패했습니다. 다시 시도해 주세요." });
    }
  });

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl font-semibold">회원가입</h1>

      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="email" className="text-sm font-medium">
            이메일
          </label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            aria-invalid={Boolean(errors.email)}
            {...register("email")}
          />
          {errors.email && (
            <p className="text-sm text-destructive">{errors.email.message}</p>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="nickname" className="text-sm font-medium">
            닉네임
          </label>
          <Input
            id="nickname"
            autoComplete="nickname"
            aria-invalid={Boolean(errors.nickname)}
            {...register("nickname")}
          />
          {errors.nickname && (
            <p className="text-sm text-destructive">{errors.nickname.message}</p>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="password" className="text-sm font-medium">
            비밀번호
          </label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
            aria-invalid={Boolean(errors.password)}
            {...register("password")}
          />
          <p className="text-xs text-muted-foreground">
            8~64자, 영문과 숫자를 포함해야 합니다.
          </p>
          {errors.password && (
            <p className="text-sm text-destructive">{errors.password.message}</p>
          )}
        </div>

        {errors.root && (
          <p className="text-sm text-destructive">{errors.root.message}</p>
        )}

        <Button type="submit" disabled={isSubmitting} className="mt-2">
          {isSubmitting ? "가입 중..." : "회원가입"}
        </Button>
      </form>

      <p className="text-center text-sm text-muted-foreground">
        이미 계정이 있으신가요?{" "}
        <Link href="/login" className="text-primary underline-offset-4 hover:underline">
          로그인
        </Link>
      </p>
    </div>
  );
}
