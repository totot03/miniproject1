"use client";

import { useId, useRef, useState } from "react";
import { useFormContext } from "react-hook-form";
import { useQuery } from "@tanstack/react-query";
import { MapPin, Search } from "lucide-react";

import { EmptyState } from "@/components/common/EmptyState";
import { PharmacyMapPickerDialog } from "@/components/location/PharmacyMapPickerDialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useDebouncedValue } from "@/hooks/useDebouncedValue";
import { apiFetch } from "@/lib/api";
import { useAppSelector } from "@/lib/hooks";
import { cn } from "@/lib/utils";
import type { ReportFormValues } from "@/lib/validation/report";
import type { components } from "@/types/api";

type PharmacySummary = components["schemas"]["PharmacySummaryResponse"];
type PharmacyPage = components["schemas"]["PageResponsePharmacySummaryResponse"];

/** DrugAutocomplete.tsx MIN_QUERY_LENGTH와 같은 기준. */
const MIN_QUERY_LENGTH = 2;
const SEARCH_SIZE = 8;

/** openapi-typescript는 필수 여부를 몰라 전부 optional로 만든다 — DrugAutocomplete.tsx hasIdAndName과 같은 패턴. */
function hasIdAndName(
  item: PharmacySummary,
): item is PharmacySummary & { id: number; name: string } {
  return typeof item.id === "number" && typeof item.name === "string";
}

function optionId(listboxId: string, pharmacyId: number): string {
  return `${listboxId}-option-${pharmacyId}`;
}

/**
 * 가격 제보 폼의 약국 선택 필드 (docs/ROADMAP.md T-29 3번).
 *
 * 이름 검색 자동완성(GET /api/v1/pharmacies?q=)과 "지도에서 고르기" 두 경로를
 * 제공한다. 선택되면 카드로 접히고, "변경"을 누르면 다시 검색 상태로 돌아간다.
 * `?pharmacyId=` 프리필은 이 컴포넌트가 아니라 app/reports/new/page.tsx가
 * react-hook-form의 초기값으로 채운다 — 여기서는 현재 폼 값만 본다.
 */
export function PharmacyPicker() {
  const {
    watch,
    setValue,
    formState: { errors },
  } = useFormContext<ReportFormValues>();

  const pharmacyId = watch("pharmacyId");
  const pharmacyName = watch("pharmacyName");
  const selected = pharmacyId > 0 && pharmacyName.length > 0;

  const listboxId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [text, setText] = useState("");
  const [isOpen, setIsOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [mapOpen, setMapOpen] = useState(false);

  // 확정된 위치가 있으면 검색·지도 중심에 재사용한다(locationSlice, T-16). 없어도
  // q만으로 검색은 되므로 새로 GPS를 요청하지는 않는다.
  const location = useAppSelector((state) => state.location);

  const debouncedText = useDebouncedValue(text, 300);
  const trimmed = debouncedText.trim();
  const query = useQuery({
    queryKey: ["pharmacies", trimmed, location.lat, location.lng],
    queryFn: () => {
      const params = new URLSearchParams({ q: trimmed, size: String(SEARCH_SIZE) });
      if (location.lat != null && location.lng != null) {
        params.set("lat", String(location.lat));
        params.set("lng", String(location.lng));
      }
      return apiFetch<PharmacyPage>(`/api/v1/pharmacies?${params.toString()}`);
    },
    enabled: trimmed.length >= MIN_QUERY_LENGTH,
  });

  const items = (query.data?.content ?? []).filter(hasIdAndName);
  const activeOption = activeIndex >= 0 ? items[activeIndex] : undefined;
  const showDropdown = isOpen && text.trim().length >= MIN_QUERY_LENGTH;

  function select(pharmacy: { id: number; name: string }) {
    setValue("pharmacyId", pharmacy.id, { shouldValidate: true });
    setValue("pharmacyName", pharmacy.name, { shouldValidate: true });
    setText("");
    setIsOpen(false);
  }

  function clearSelection() {
    setValue("pharmacyId", 0, { shouldValidate: false });
    setValue("pharmacyName", "", { shouldValidate: false });
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
          select(items[activeIndex]);
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
    <div className="flex flex-col gap-1.5">
      <span className="text-sm font-medium">약국</span>

      {selected ? (
        <div className="flex items-center justify-between rounded-lg border px-3 py-2 text-sm">
          <span className="font-medium">{pharmacyName}</span>
          <Button type="button" variant="ghost" size="sm" onClick={clearSelection}>
            변경
          </Button>
        </div>
      ) : (
        <div className="space-y-2">
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
              aria-label="약국 검색"
              placeholder="약국 이름을 입력하세요 (예: 가온약국)"
              className="h-11 pl-9 text-base"
              value={text}
              onChange={(event) => {
                setText(event.target.value);
                setIsOpen(true);
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
                  </div>
                ) : items.length === 0 ? (
                  <EmptyState
                    title="검색 결과가 없습니다"
                    description="다른 약국 이름으로 다시 검색해 보세요."
                    className="border-none py-6"
                  />
                ) : (
                  <ul
                    id={listboxId}
                    role="listbox"
                    aria-label="약국 검색 결과"
                    className="max-h-72 overflow-y-auto py-1"
                  >
                    {items.map((item, index) => (
                      <li
                        key={item.id}
                        id={optionId(listboxId, item.id)}
                        role="option"
                        aria-selected={index === activeIndex}
                        className={cn(
                          "flex cursor-pointer flex-col gap-0.5 px-3 py-2 text-sm",
                          index === activeIndex && "bg-muted",
                        )}
                        onMouseEnter={() => setActiveIndex(index)}
                        // mousedown에서 preventDefault해야 클릭 전에 input이 blur되지 않는다
                        // (DrugAutocomplete.tsx와 같은 이유).
                        onMouseDown={(event) => {
                          event.preventDefault();
                          select(item);
                        }}
                      >
                        <span className="font-medium">{item.name}</span>
                        {item.addressRoad ? (
                          <span className="text-muted-foreground text-xs">
                            {item.addressRoad}
                          </span>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ) : null}
          </div>

          <Button type="button" variant="outline" size="sm" onClick={() => setMapOpen(true)}>
            <MapPin className="size-4" />
            지도에서 고르기
          </Button>
        </div>
      )}

      {errors.pharmacyId && (
        <p className="text-sm text-destructive">{errors.pharmacyId.message}</p>
      )}

      <PharmacyMapPickerDialog
        open={mapOpen}
        onOpenChange={setMapOpen}
        onSelect={(pharmacy) => {
          select(pharmacy);
          setMapOpen(false);
        }}
      />
    </div>
  );
}
