import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";

/** /search 로딩 중 표시 (Next.js loading.js 파일 컨벤션). */
export default function SearchLoading() {
  return <LoadingSkeleton count={5} className="mx-auto max-w-5xl px-4 py-8" />;
}
