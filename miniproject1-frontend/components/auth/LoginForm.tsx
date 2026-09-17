"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useAppDispatch } from "@/lib/hooks";
import { login, type AuthThunkError } from "@/lib/slices/authSlice";
import { loginSchema, type LoginFormValues } from "@/lib/validation/auth";

interface LoginFormProps {
  /** 로그인 성공 후 돌아갈 경로. proxy.ts가 보호 경로에서 리다이렉트할 때 붙인다 */
  next: string;
  /** 가입 직후 넘어온 경우 안내 메시지를 보여준다 */
  signedUp?: boolean;
}

export function LoginForm({ next, signedUp = false }: LoginFormProps) {
  const dispatch = useAppDispatch();
  const router = useRouter();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    const result = await dispatch(login(values));
    if (login.rejected.match(result)) {
      // fieldErrors가 있으면 필드별로, 없으면(예: 401) 루트 에러로 보여준다.
      // docs/API.md §2 — 로그인은 이메일/비밀번호 중 무엇이 틀렸는지
      // 구분해 응답하지 않으므로 필드 매핑 대신 공통 메시지를 쓴다.
      const rejected = (result.payload ?? {
        code: "INTERNAL_ERROR",
        message: "로그인에 실패했습니다.",
      }) as AuthThunkError;

      if (rejected.fieldErrors?.length) {
        for (const fieldError of rejected.fieldErrors) {
          if (fieldError.field === "email" || fieldError.field === "password") {
            setError(fieldError.field, { message: fieldError.reason });
          }
        }
        return;
      }

      setError("root", { message: rejected.message });
      return;
    }

    router.push(next);
  });

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-xl font-semibold">로그인</h1>

      {signedUp && (
        <p className="rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">
          가입이 완료되었습니다. 로그인해 주세요.
        </p>
      )}

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
          <label htmlFor="password" className="text-sm font-medium">
            비밀번호
          </label>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={Boolean(errors.password)}
            {...register("password")}
          />
          {errors.password && (
            <p className="text-sm text-destructive">{errors.password.message}</p>
          )}
        </div>

        {errors.root && (
          <p className="text-sm text-destructive">{errors.root.message}</p>
        )}

        <Button type="submit" disabled={isSubmitting} className="mt-2">
          {isSubmitting ? "로그인 중..." : "로그인"}
        </Button>
      </form>

      <p className="text-center text-sm text-muted-foreground">
        아직 계정이 없으신가요?{" "}
        <Link href="/signup" className="text-primary underline-offset-4 hover:underline">
          회원가입
        </Link>
      </p>
    </div>
  );
}
