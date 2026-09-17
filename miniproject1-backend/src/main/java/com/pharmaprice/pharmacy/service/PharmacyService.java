package com.pharmaprice.pharmacy.service;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;

/** {@code docs/ROADMAP.md} T-19 / {@code docs/API.md} §4. */
public interface PharmacyService {

    PageResponse<PharmacySummaryResponse> list(String q, Double lat, Double lng, Integer radius, int page, int size);

    PharmacyDetailResponse getDetail(Long pharmacyId, Double lat, Double lng);
}
