"use client";

import { useEffect, useId, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";

import { EmptyState } from "@/components/common/EmptyState";
import { RegionPicker } from "@/components/location/RegionPicker";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { useUserLocation } from "@/hooks/useUserLocation";
import { apiFetch } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { components } from "@/types/api";

type DrugSummary = components["schemas"]["DrugSummaryResponse"];
type DrugPage = components["schemas"]["PageResponseDrugSummaryResponse"];

/** 검색 API 호출 없이 바로 보여줄 인기 약품(docs/ROADMAP.md T-17 1번, 6~8개). 실제 존재 여부와 무관하게 입력창을 채우는 프리셋일 뿐이다. */
const POPULAR_DRUG_CHIPS = [
  "타이레놀",
  "게보린",
  "판콜에이",
  "베아제",
  "부루펜",
  "챔프",
  "판피린",
  "훼스탈",
] as const;

/** 자동완성으로 넘어가는 최소 글자 수(docs/ROADMAP.md T-17 2번) — 1글자 검색은 후보가 너무 많아 의미가 없다. */
const MIN_QUERY_LENGTH = 2;
/** docs/API.md §3 "자동완성 용도로는 size=8로 호출" */
const AUTOCOMPLETE_SIZE = 8;
/** docs/ROADMAP.md T-17 5번 고정 반경 */
const SEARCH_RADIUS_M = 2000;

/** openapi-typescript가 필수 여부를 모르면 전부 optional로 만든다 — 실제로 없을 리 없는 id/displayName을 렌더링 전에 걸러낸다(RegionPicker의 hasSido/hasCode와 같은 방식). */
function hasIdAndName(
  item: DrugSummary,
): item is DrugSummary & { id: number; displayName: string } {
  return typeof item.id === "number" && typeof item.displayName === "string";
}

function optionId(listboxId: string, drugId: number): string {
  return `${listboxId}-option-${drugId}`;
}

/**
 * 홈 화면 검색 섹션 전체(입력창 + 인기 약품 칩 + 자동완성 드롭다운)를 소유하는
 * 컴포넌트 (docs/ROADMAP.md T-17).
 *
 * ARIA 1.2 콤보박스 패턴을 직접 구현한다 — shadcn Command(cmdk)는 명령
 * 팔레트용 포커스 모델(roving tabindex)을 쓰지만, 여기서 요구되는 건
 * "입력 포커스는 그대로 두고 aria-activedescendant로 활성 항목만 가리키는"
 * 표준 콤보박스 모델이라 서로 다르다. shadcn 레지스트리 연결도 현재 끊겨
 * 있어 새 컴포넌트를 들여오는 대신 기존 Input + 직접 만든 listbox로 만든다.
 */
export function DrugAutocomplete() {
  const router = useRouter();
  const listboxId = useId();
  const inputRef = useRef<HTMLInputElement>(null);

  const [text, setText] = useState("");
  const [isOpen, setIsOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [regionPickerOpen, setRegionPickerOpen] = useState(false);
  // 위치가 아직 확정되지 않은 상태에서 항목을 선택한 경우, 위치가 확정될
  // 때까지 이동을 미뤄두는 임시 저장소 (docs/ROADMAP.md T-17 6번). 화면에
  // 표시할 필요가 없는 값이라 리렌더를 일으키지 않는 ref로 둔다 — 그래야
  // 아래 effect에서 setState 없이(react-hooks/set-state-in-effect 규칙)
  // 위치 확정 시점에 값을 읽고 지울 수 있다.
  const pendingDrugIdRef = useRef<number | null>(null);

  const {
    state: locationState,
    requestGpsLocation,
    selectRegionFallback,
  } = useUserLocation();

  const debouncedText = useDebouncedValue(text, 300);
  const trimmed = debouncedText.trim();
  const query = useQuery({
    queryKey: ["drugs", trimmed],
    queryFn: () =>
      apiFetch<DrugPage>(
        `/api/v1/drugs?q=${encodeURIComponent(trimmed)}&size=${AUTOCOMPLETE_SIZE}`,
      ),
    enabled: trimmed.length >= MIN_QUERY_LENGTH,
  });

  const items = (query.data?.content ?? []).filter(hasIdAndName);
  const activeOption = activeIndex >= 0 ? items[activeIndex] : undefined;
  const showDropdown = isOpen && text.trim().length >= MIN_QUERY_LENGTH;

  function navigate(drugId: number, lat: number, lng: number) {
    router.push(
      `/search?drugId=${drugId}&lat=${lat}&lng=${lng}&radius=${SEARCH_RADIUS_M}`,
    );
  }

  function handleSelect(drugId: number) {
    setIsOpen(false);
    if (locationState.status === "granted" || locationState.status === "fallback") {
      navigate(drugId, locationState.lat, locationState.lng);
      return;
    }
    // 위치가 아직 없다 — 선택을 기억해뒀다가 아래 effect가 위치 확정 시
    // 이어서 이동시킨다.
    pendingDrugIdRef.current = drugId;
    if (locationState.status === "idle") {
      // useUserLocation의 진행 상태(idle/requesting/denied/unavailable)는
      // 훅 인스턴스마다 따로 관리되는 로컬 state라(locationSlice.ts 참고),
      // 헤더(LocationIndicator)가 이미 GPS를 요청했더라도 이 컴포넌트의
      // 훅 인스턴스는 그 사실을 모른다 — 직접 요청해야 한다.
      requestGpsLocation(() => setRegionPickerOpen(true));
    } else if (locationState.status === "denied" || locationState.status === "unavailable") {
      setRegionPickerOpen(true);
    }
    // "requesting"이면 이 인스턴스가 이미 요청을 보낸 상태라 추가 조치 없이
    // 기다린다 — 아래 effect가 결과(성공/실패)를 이어받는다.
  }

  // locationState가 바뀔 때(GPS 응답, RegionPicker 선택)마다 보류 중인
  // 선택이 있는지 확인해 이동시킨다. ref만 읽고 쓰므로 setState가 없어
  // react-hooks/set-state-in-effect 규칙에 걸리지 않는다.
  useEffect(() => {
    if (pendingDrugIdRef.current == null) return;
    if (locationState.status === "granted" || locationState.status === "fallback") {
      navigate(pendingDrugIdRef.current, locationState.lat, locationState.lng);
      pendingDrugIdRef.current = null;
    }
    // navigate는 router에서 파생된 안정적인 함수라 의존성에서 제외해도 무방하다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [locationState]);

  function handleChipClick(label: string) {
    setText(label);
    setIsOpen(true);
    setActiveIndex(-1);
    inputRef.current?.focus();
  }

  function handleKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (!showDropdown || items.length === 0) {
      if (event.key === "Escape") setIsOpen(false);
      return;
    }

    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        setIsOpen(true);
        setActiveIndex((prev) => (prev + 1) % items.length);
        break;
      case "ArrowUp":
        event.preventDefault();
        setIsOpen(true);
        setActiveIndex((prev) => (prev <= 0 ? items.length - 1 : prev - 1));
        break;
      case "Enter":
        if (activeIndex >= 0 && items[activeIndex]) {
          event.preventDefault();
          handleSelect(items[activeIndex].id);
        }
        break;
      case "Escape":
        setIsOpen(false);
        break;
      default:
        break;
    }
  }

  return (
    <div className="space-y-4">
      <div className="relative">
        <Search
          aria-hidden="true"
          className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2"
        />
        <Input
          ref={inputRef}
          role="combobox"
          aria-autocomplete="list"
          aria-expanded={showDropdown}
          aria-controls={listboxId}
          aria-activedescendant={
            activeOption ? optionId(listboxId, activeOption.id) : undefined
          }
          aria-label="약품 검색"
          placeholder="약품 이름을 입력하세요 (예: 타이레놀)"
          className="h-11 pl-9 text-base"
          value={text}
          onChange={(event) => {
            setText(event.target.value);
            setIsOpen(true);
            // 검색어가 바뀌면 이전 activeIndex는 더 이상 유효하지 않다.
            setActiveIndex(-1);
          }}
          onFocus={() => {
            if (text.trim().length >= MIN_QUERY_LENGTH) setIsOpen(true);
          }}
          onBlur={() => setIsOpen(false)}
          onKeyDown={handleKeyDown}
        />

        {showDropdown ? (
          <div className="bg-popover text-popover-foreground absolute z-10 mt-1 w-full rounded-lg border shadow-md">
            {query.isLoading ? (
              <div className="space-y-2 p-3" aria-hidden="true">
                <Skeleton className="h-9 w-full" />
                <Skeleton className="h-9 w-full" />
                <Skeleton className="h-9 w-full" />
              </div>
            ) : items.length === 0 ? (
              <EmptyState
                title="검색 결과가 없습니다"
                description="다른 약품 이름으로 다시 검색해 보세요."
                className="border-none py-6"
              />
            ) : (
              <ul
                id={listboxId}
                role="listbox"
                aria-label="약품 검색 결과"
                className="max-h-72 overflow-y-auto py-1"
              >
                {items.map((item, index) => (
                  <li
                    key={item.id}
                    id={optionId(listboxId, item.id)}
                    role="option"
                    aria-selected={index === activeIndex}
                    className={cn(
                      "flex cursor-pointer items-baseline justify-between gap-3 px-3 py-2 text-sm",
                      index === activeIndex && "bg-muted",
                    )}
                    onMouseEnter={() => setActiveIndex(index)}
                    // mousedown에서 preventDefault해야 클릭 전에 input이
                    // blur되지 않는다 — blur되면 onBlur가 드롭다운을 먼저
                    // 닫아버려 클릭이 씹힌다.
                    onMouseDown={(event) => {
                      event.preventDefault();
                      handleSelect(item.id);
                    }}
                  >
                    <span className="font-medium">{item.displayName}</span>
                    <span className="text-muted-foreground shrink-0 text-xs">
                      {item.packageUnit ?? ""}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ) : null}
      </div>

      <div className="flex flex-wrap gap-2">
        {POPULAR_DRUG_CHIPS.map((label) => (
          <Button
            key={label}
            type="button"
            variant="outline"
            size="sm"
            onClick={() => handleChipClick(label)}
          >
            {label}
          </Button>
        ))}
      </div>

      <RegionPicker
        open={regionPickerOpen}
        onOpenChange={setRegionPickerOpen}
        onSelect={selectRegionFallback}
      />
    </div>
  );
}
