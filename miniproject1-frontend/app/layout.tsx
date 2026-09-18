import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { Toaster } from "sonner";

import { DisclaimerBanner } from "@/components/layout/DisclaimerBanner";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { SiteHeader } from "@/components/layout/SiteHeader";

import { Providers } from "./providers";
import "./globals.css";

// Geist는 라틴 서브셋만 제공한다. 한글 폴백 스택은 globals.css의
// --font-sans에 이어붙여 두었다.
const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "약값알림 — 동네 약국 일반의약품 최저가",
  description:
    "내 주변 약국의 일반의약품 가격을 비교해 최저가를 추천합니다. 가격은 학습용 예시 데이터입니다.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="ko"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <Providers>
          <SiteHeader />
          {/* 고지 배너는 루트 레이아웃에 둔다. 페이지마다 넣으면 새 라우트를
              추가할 때 빠뜨린다 (docs/PRD.md §9). */}
          <DisclaimerBanner />
          <main className="flex-1">{children}</main>
          <SiteFooter />
        </Providers>
        {/* 제보 폼(T-29) 성공 알림용. 페이지 이동 뒤에도 보이도록 루트에 한 번만 마운트한다. */}
        <Toaster richColors position="top-center" />
      </body>
    </html>
  );
}
