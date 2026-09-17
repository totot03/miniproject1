import { LoginForm } from "@/components/auth/LoginForm";

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/** "/"로 시작하는 상대 경로만 신뢰한다 — 오픈 리다이렉트 방지 */
function safeNext(value: string | undefined): string {
  if (value && value.startsWith("/") && !value.startsWith("//")) {
    return value;
  }
  return "/";
}

/**
 * 로그인 화면 (docs/ROADMAP.md T-25).
 *
 * Next.js 16의 searchParams는 Promise다(node_modules/next/dist/docs/
 * 01-app/03-api-reference/03-file-conventions/page.md). app/search/page.tsx와
 * 같은 관례로 서버에서 읽어 클라이언트 폼에 넘긴다.
 */
export default async function LoginPage(props: PageProps<"/login">) {
  const params = await props.searchParams;
  const next = safeNext(first(params.next));
  const signedUp = first(params.signedUp) === "1";

  return <LoginForm next={next} signedUp={signedUp} />;
}
