"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const LINKS = [
  { href: "/admin", label: "대시보드" },
  { href: "/admin/reports", label: "제보 관리" },
  { href: "/admin/stats", label: "통계" },
] as const;

/**
 * 관리자 화면 3개(대시보드/제보 관리/통계) 사이를 오가는 공용 서브 내비게이션.
 *
 * T-33에서는 헤더의 "관리자" 링크가 `/admin`으로만 연결되고 거기서
 * `/admin/reports`로 넘어가는 경로가 없었다 — URL을 직접 쳐야만 닿았다.
 * T-34에서 `/admin/stats`가 하나 더 생기기 전에 세 페이지가 공유하는 이
 * 내비게이션을 추가해, 실제로 UI에서 세 화면 모두에 도달할 수 있게 한다.
 */
export function AdminNav() {
  const pathname = usePathname();

  return (
    <nav className="flex flex-wrap gap-1.5" aria-label="관리자 메뉴">
      {LINKS.map((link) => {
        const active = pathname === link.href;
        return (
          <Button
            key={link.href}
            asChild
            size="sm"
            variant={active ? "secondary" : "ghost"}
            className={cn(active && "pointer-events-none")}
          >
            <Link href={link.href} aria-current={active ? "page" : undefined}>
              {link.label}
            </Link>
          </Button>
        );
      })}
    </nav>
  );
}
