"use client";

import { useState } from "react";
import { ChevronDown, Clock } from "lucide-react";

import { cn } from "@/lib/utils";

export interface BusinessHoursProps {
  businessHours: Record<string, string[]>;
  className?: string;
}

/** docs/API.md §4 businessHours 키 순서 + 한글 라벨. */
const DAY_LABELS: readonly [key: string, label: string][] = [
  ["mon", "월"],
  ["tue", "화"],
  ["wed", "수"],
  ["thu", "목"],
  ["fri", "금"],
  ["sat", "토"],
  ["sun", "일"],
  ["holiday", "공휴일"],
];

/** Intl "short" 요일 표기(ko-KR)는 DAY_LABELS의 라벨("월","화",...)과 정확히 같은 문자열을 낸다. */
const KOREAN_WEEKDAY_TO_KEY: Record<string, string> = {
  월: "mon",
  화: "tue",
  수: "wed",
  목: "thu",
  금: "fri",
  토: "sat",
  일: "sun",
};

/**
 * Asia/Seoul 기준 오늘의 businessHours 키.
 *
 * 명시적으로 Asia/Seoul을 고정하므로 브라우저 시스템 타임존과 무관하게 서버
 * 렌더와 같은 값을 낸다 — 클라이언트 컴포넌트로 옮기면서도 하이드레이션
 * 불일치가 생기지 않는 이유다.
 */
function todayKey(): string {
  const weekday = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    weekday: "short",
  }).format(new Date());
  return KOREAN_WEEKDAY_TO_KEY[weekday] ?? "";
}

function DayRow({
  label,
  hours,
  isToday,
}: {
  label: string;
  hours: string[] | undefined;
  isToday: boolean;
}) {
  const isOpen = hours && hours.length === 2;
  return (
    <div
      className={cn(
        "flex items-center justify-between gap-3 rounded px-1.5 py-1",
        isToday && "bg-primary/10",
      )}
    >
      <dt className={cn("font-medium", isToday && "text-primary")}>
        {label}
        {isToday ? <span className="ml-1 text-[10px] font-normal">오늘</span> : null}
      </dt>
      <dd className={cn("tabular-nums", isOpen ? "text-foreground" : "text-muted-foreground")}>
        {isOpen ? `${hours[0]} ~ ${hours[1]}` : "휴무"}
      </dd>
    </div>
  );
}

/**
 * 약국 상세 영업시간 박스 (app/pharmacies/[id]/page.tsx).
 *
 * 기본은 "지금 열었나"만 보이면 되는 오늘 요일 한 줄만 보여준다 — 8줄을
 * 한꺼번에 늘어놓을 필요가 없다. 클릭하면 나머지 요일이 펼쳐진다
 * (components/DrugPriceRow.tsx 가격 이력 펼치기와 같은 패턴). 서버
 * 컴포넌트인 페이지에서 이 부분만 상호작용이 필요해 클라이언트 경계로
 * 분리했다.
 */
export function BusinessHours({ businessHours, className }: BusinessHoursProps) {
  const [expanded, setExpanded] = useState(false);
  const today = todayKey();
  const todayLabel = DAY_LABELS.find(([key]) => key === today)?.[1] ?? "";
  const otherDays = DAY_LABELS.filter(([key]) => key !== today);

  return (
    <div className={cn("bg-muted/30 max-w-sm rounded-lg border sm:max-w-md", className)}>
      <button
        type="button"
        aria-expanded={expanded}
        onClick={() => setExpanded((prev) => !prev)}
        className="hover:bg-muted/50 flex w-full items-center gap-1.5 rounded-t-lg px-3 pt-2 pb-1 text-left"
      >
        <Clock aria-hidden="true" className="text-muted-foreground size-3.5 shrink-0" />
        <span className="text-muted-foreground text-xs font-semibold">영업시간</span>
        <ChevronDown
          aria-hidden="true"
          className={cn(
            "text-muted-foreground ml-auto size-3.5 shrink-0 transition-transform",
            expanded && "rotate-180",
          )}
        />
      </button>
      <dl className="space-y-0.5 px-3 pb-2 text-xs">
        <DayRow label={todayLabel} hours={businessHours[today]} isToday />
        {expanded
          ? otherDays.map(([key, label]) => (
              <DayRow key={key} label={label} hours={businessHours[key]} isToday={false} />
            ))
          : null}
      </dl>
    </div>
  );
}
