package com.pharmaprice.pharmacy.controller;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse;
import com.pharmaprice.pharmacy.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code docs/API.md} §4 약국 검색(제보 폼의 약국 선택용)·상세.
 *
 * <p>검증(q/lat+lng 필수 조합, 좌표 범위)은 전부 {@link PharmacyService}가 맡는다 —
 * {@code DrugController}처럼 파라미터를 그대로 위임만 한다.</p>
 */
@RestController
@RequestMapping("/api/v1/pharmacies")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping
    public PageResponse<PharmacySummaryResponse> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Double lat,
        @RequestParam(required = false) Double lng,
        @RequestParam(required = false) Integer radius,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return pharmacyService.list(q, lat, lng, radius, page, size);
    }

    @GetMapping("/{pharmacyId}")
    public PharmacyDetailResponse detail(
        @PathVariable Long pharmacyId,
        @RequestParam(required = false) Double lat,
        @RequestParam(required = false) Double lng
    ) {
        return pharmacyService.getDetail(pharmacyId, lat, lng);
    }

    /** {@code docs/API.md} §4 history. days 검증(기본180/최대365 clamp)은 전부 {@link PharmacyService} 몫이다. */
    @GetMapping("/{pharmacyId}/drugs/{drugId}/history")
    public PriceHistoryResponse history(
        @PathVariable Long pharmacyId,
        @PathVariable Long drugId,
        @RequestParam(required = false) Integer days
    ) {
        return pharmacyService.getPriceHistory(pharmacyId, drugId, days);
    }
}
