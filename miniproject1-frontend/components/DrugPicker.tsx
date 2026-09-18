"use client";

import { useFormContext } from "react-hook-form";

import { DrugAutocomplete } from "@/components/DrugAutocomplete";
import { Button } from "@/components/ui/button";
import type { ReportFormValues } from "@/lib/validation/report";

/**
 * 가격 제보 폼의 약품 선택 필드 (docs/ROADMAP.md T-29 4번).
 *
 * `DrugAutocomplete`(T-17)는 홈 화면에서 "선택 → /search로 이동"이 기본
 * 동작이라 폼에는 맞지 않는다. 대신 `onSelect`를 넘겨 이동 분기를 건너뛰고
 * 여기서 react-hook-form 값만 채우도록 재사용한다 — 콤보박스 UI·ARIA·
 * 키보드 탐색 코드를 중복시키지 않기 위함이다.
 *
 * 마스터에 없는 약품은 애초에 목록에 뜨지 않으므로 자유 입력은 자연히
 * 막힌다(ROADMAP 4번 "자유 입력을 허용하지 않는다").
 */
export function DrugPicker() {
  const {
    watch,
    setValue,
    formState: { errors },
  } = useFormContext<ReportFormValues>();

  const drugId = watch("drugId");
  const drugName = watch("drugName");
  const selected = drugId > 0 && drugName.length > 0;

  function clearSelection() {
    setValue("drugId", 0, { shouldValidate: false });
    setValue("drugName", "", { shouldValidate: false });
  }

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-sm font-medium">약품</span>

      {selected ? (
        <div className="flex items-center justify-between rounded-lg border px-3 py-2 text-sm">
          <span className="font-medium">{drugName}</span>
          <Button type="button" variant="ghost" size="sm" onClick={clearSelection}>
            변경
          </Button>
        </div>
      ) : (
        <DrugAutocomplete
          onSelect={(drug) => {
            setValue("drugId", drug.id, { shouldValidate: true });
            setValue("drugName", drug.displayName, { shouldValidate: true });
          }}
        />
      )}

      {errors.drugId && (
        <p className="text-sm text-destructive">{errors.drugId.message}</p>
      )}
    </div>
  );
}
