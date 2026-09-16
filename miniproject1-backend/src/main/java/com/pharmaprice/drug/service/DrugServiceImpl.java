package com.pharmaprice.drug.service;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugDetailResponse.PriceStats;
import com.pharmaprice.drug.dto.DrugSummaryResponse;
import com.pharmaprice.drug.repository.DrugQueryRepository;
import com.pharmaprice.drug.repository.DrugQueryRepository.DrugDetailRow;
import com.pharmaprice.drug.repository.DrugQueryRepository.DrugSearchRow;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DrugServiceImpl implements DrugService {

    /** {@code docs/ROADMAP.md} T-13 "size는 최대 50으로 clamp". */
    private static final int MAX_PAGE_SIZE = 50;

    private final DrugQueryRepository drugQueryRepository;

    @Override
    public PageResponse<DrugSummaryResponse> search(String q, String category, int page, int size) {
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long offset = (long) safePage * clampedSize;

        var rows = drugQueryRepository.search(q, category, clampedSize, offset);
        long totalElements = drugQueryRepository.countSearch(q, category);

        var content = rows.stream().map(this::toSummary).toList();
        return PageResponse.of(content, safePage, clampedSize, totalElements);
    }

    @Override
    public DrugDetailResponse getDetail(Long drugId) {
        DrugDetailRow row = drugQueryRepository.findDetail(drugId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "drug not found: " + drugId));
        return toDetail(row);
    }

    private DrugSummaryResponse toSummary(DrugSearchRow row) {
        return new DrugSummaryResponse(
            row.getId(), row.getItemSeq(), row.getDisplayName(), row.getName(), row.getMaker(),
            row.getCategory(), row.getForm(), row.getPackageUnit(), row.getImageUrl(),
            row.getNationalAvgPrice(), row.getPharmacyCount());
    }

    private DrugDetailResponse toDetail(DrugDetailRow row) {
        PriceStats priceStats = new PriceStats(
            row.getNationalAvg(), row.getNationalMin(), row.getNationalMax(),
            row.getPharmacyCount(), row.getReportCount());
        return new DrugDetailResponse(
            row.getId(), row.getItemSeq(), row.getDisplayName(), row.getName(), row.getMaker(),
            row.getCategory(), row.getForm(), row.getPackageUnit(), row.getImageUrl(), priceStats);
    }
}
