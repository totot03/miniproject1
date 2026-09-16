package com.pharmaprice.drug.service;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugSummaryResponse;

/**
 * {@code docs/API.md} §3 의약품 검색/상세 조회. {@code DrugQueryRepository}의
 * 원시 프로젝션을 응답 DTO로 조립하는 계층이다.
 */
public interface DrugService {

    PageResponse<DrugSummaryResponse> search(String q, String category, int page, int size);

    /** 전문의약품이거나 존재하지 않으면 404({@code ResponseStatusException})를 던진다. */
    DrugDetailResponse getDetail(Long drugId);
}
