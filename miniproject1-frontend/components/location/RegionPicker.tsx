"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { ErrorState } from "@/components/common/ErrorState";
import { LoadingSkeleton } from "@/components/common/LoadingSkeleton";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { apiFetch } from "@/lib/api";
import type { components } from "@/types/api";

type RegionGroup = components["schemas"]["RegionGroupResponse"];
type SigunguItem = components["schemas"]["SigunguItem"];

/** openapi-typescript는 필수 여부를 모르면 모든 필드를 optional로 만든다 — 실제로 비어 있을 리 없는 필드를 렌더링 전에 걸러낸다. */
function hasSido(group: RegionGroup): group is RegionGroup & { sido: string } {
  return typeof group.sido === "string" && group.sido.length > 0;
}

function hasCode(item: SigunguItem): item is SigunguItem & { code: string } {
  return typeof item.code === "string" && item.code.length > 0;
}

export interface RegionPickerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (region: {
    regionCode: string;
    lat: number;
    lng: number;
    label: string;
  }) => void;
}

/**
 * 위치 권한 거부·실패 시 뜨는 시·도 → 시·군·구 2단 선택 모달
 * (docs/ROADMAP.md T-16, docs/API.md §7).
 *
 * `GET /api/v1/regions`(T-14)가 이미 시도별로 그룹핑해 내려주므로, 화면에서
 * 다시 묶을 필요 없이 그대로 1단계 옵션으로 쓴다. `pharmacyCount === 0`인
 * 시·군·구는 선택해도 검색 결과가 나올 수 없으므로 비활성화한다(ROADMAP 7번).
 */
export function RegionPicker({
  open,
  onOpenChange,
  onSelect,
}: RegionPickerProps) {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["regions"],
    queryFn: () => apiFetch<RegionGroup[]>("/api/v1/regions"),
    // 세션 중 지역 목록이 바뀌지 않는다 (ROADMAP T-16 5번).
    staleTime: Infinity,
  });

  const [sido, setSido] = useState<string | undefined>(undefined);
  const [sigunguCode, setSigunguCode] = useState<string | undefined>(undefined);

  const sidoGroups = useMemo(() => (data ?? []).filter(hasSido), [data]);

  const sigungus = useMemo<Array<SigunguItem & { code: string }>>(() => {
    return (sidoGroups.find((group) => group.sido === sido)?.sigungus ?? []).filter(
      hasCode,
    );
  }, [sidoGroups, sido]);

  const selected = sigungus.find((item) => item.code === sigunguCode);
  const canConfirm =
    Boolean(sido) &&
    selected !== undefined &&
    Boolean(selected.pharmacyCount) &&
    selected.centerLat != null &&
    selected.centerLng != null;

  function handleSidoChange(value: string) {
    setSido(value);
    setSigunguCode(undefined);
  }

  function handleConfirm() {
    if (!canConfirm || !selected || !sido) return;
    // canConfirm이 centerLat/centerLng의 존재를 이미 보장하지만, 타입 좁히기를 위해 다시 확인한다.
    if (selected.centerLat == null || selected.centerLng == null) return;
    onSelect({
      regionCode: selected.code,
      lat: selected.centerLat,
      lng: selected.centerLng,
      label: `${sido} ${selected.sigungu ?? ""}`.trim(),
    });
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>지역 선택</DialogTitle>
          <DialogDescription>
            위치를 사용할 수 없어요. 검색 기준으로 쓸 지역을 골라주세요.
          </DialogDescription>
        </DialogHeader>

        {isLoading ? (
          <LoadingSkeleton count={2} />
        ) : isError ? (
          <ErrorState
            error={error}
            fallbackMessage="지역 목록을 불러오지 못했습니다."
            onRetry={() => refetch()}
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            <Select value={sido} onValueChange={handleSidoChange}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="시·도" />
              </SelectTrigger>
              {/* 기본값인 item-aligned는 선택된 항목을 트리거에 맞춰 정렬하려다
                  목록이 길 때(경기도 47개 시군구 등) Dialog 안에서 스크롤 위치
                  계산이 꼬여 부자연스럽다 — 일반적인 드롭다운처럼 트리거
                  아래에 붙는 popper로 바꿔 스크롤이 항상 위→아래로 자연스럽게
                  흐르게 한다. */}
              <SelectContent position="popper">
                {sidoGroups.map((group) => (
                  <SelectItem key={group.sido} value={group.sido}>
                    {group.sido}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              value={sigunguCode}
              onValueChange={setSigunguCode}
              disabled={!sido}
            >
              <SelectTrigger className="w-full">
                <SelectValue placeholder="시·군·구" />
              </SelectTrigger>
              <SelectContent position="popper">
                {sigungus.map((item) => (
                  <SelectItem
                    key={item.code}
                    value={item.code}
                    disabled={!item.pharmacyCount}
                  >
                    {item.sigungu}
                    {!item.pharmacyCount ? " (약국 없음)" : ""}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        <DialogFooter>
          <Button onClick={handleConfirm} disabled={!canConfirm}>
            이 지역으로 검색
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
