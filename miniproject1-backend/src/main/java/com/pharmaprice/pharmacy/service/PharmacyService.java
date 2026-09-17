package com.pharmaprice.pharmacy.service;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse;

/** {@code docs/ROADMAP.md} T-19/T-20 / {@code docs/API.md} §4. */
public interface PharmacyService {

    PageResponse<PharmacySummaryResponse> list(String q, Double lat, Double lng, Integer radius, int page, int size);

    PharmacyDetailResponse getDetail(Long pharmacyId, Double lat, Double lng);

    /** T-20. {@code days} 가 {@code null}이면 기본 180일, 최대 365일로 clamp한다. */
    PriceHistoryResponse getPriceHistory(Long pharmacyId, Long drugId, Integer days);
}
