import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

/**
 * 가격 제보 폼의 임시 입력 상태.
 *
 * 약국 상세에서 "이 약국에 가격 제보"를 누르면 약국 선택이 이미 끝난 채로
 * /reports/new에 도착해야 한다. 화면을 넘어 유지되는 입력이라 Redux가
 * 맡는다 (docs/PRD.md §4.2).
 *
 * 폼 내부의 필드별 검증 상태는 React Hook Form이 맡고, 여기에는 화면을
 * 넘길 값만 둔다.
 *
 * 제보 폼 전체는 T-29에서 채운다.
 */

export interface ReportDraftState {
  pharmacyId: number | null;
  pharmacyName: string | null;
  drugId: number | null;
  drugName: string | null;
  price: number | null;
  /** YYYY-MM-DD (KST 기준). 미래이거나 180일 초과 과거는 거부된다 */
  purchasedAt: string | null;
  /** 업로드된 영수증 파일 id (T-27) */
  uploadedFileId: number | null;
}

const initialState: ReportDraftState = {
  pharmacyId: null,
  pharmacyName: null,
  drugId: null,
  drugName: null,
  price: null,
  purchasedAt: null,
  uploadedFileId: null,
};

const reportDraftSlice = createSlice({
  name: "reportDraft",
  initialState,
  reducers: {
    /** 약국 상세에서 제보 화면으로 넘어갈 때 약국을 미리 채운다 (T-29) */
    prefillPharmacy(
      state,
      action: PayloadAction<{ pharmacyId: number; pharmacyName: string }>,
    ) {
      state.pharmacyId = action.payload.pharmacyId;
      state.pharmacyName = action.payload.pharmacyName;
    },
    /** 제보 성공 후 초기화한다 (T-29) */
    resetDraft() {
      return initialState;
    },
  },
});

export const { prefillPharmacy, resetDraft } = reportDraftSlice.actions;
export default reportDraftSlice.reducer;
