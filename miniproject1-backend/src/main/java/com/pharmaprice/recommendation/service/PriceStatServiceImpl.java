package com.pharmaprice.recommendation.service;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.repository.PriceStatRepository;
import com.pharmaprice.recommendation.repository.PriceStatRepository.PriceAggregate;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PriceStatServiceImpl implements PriceStatService {

    private final PriceStatRepository priceStatRepository;
    private final RecommendationProperties properties;

    @Override
    @Transactional
    public Optional<PharmacyDrugPriceStat> recalculate(long pharmacyId, long drugId) {
        int windowDays = properties.priceWindowDays();
        PriceAggregate aggregate = priceStatRepository.aggregate(pharmacyId, drugId, windowDays);

        // 90일 창에 유효 제보가 없으면 180일로 확대해 재시도한다.
        if (aggregate.getReportCount() == 0) {
            windowDays = properties.priceWindowFallbackDays();
            aggregate = priceStatRepository.aggregate(pharmacyId, drugId, windowDays);
        }

        // 180일로 확대해도 유효 제보가 없으면, 검색 결과에서 자동으로 빠지도록 행을 삭제한다.
        if (aggregate.getReportCount() == 0) {
            priceStatRepository.deleteByPharmacyIdAndDrugId(pharmacyId, drugId);
            return Optional.empty();
        }

        LocalDate lastReportedAt = priceStatRepository.findLastReportedAt(pharmacyId, drugId, windowDays);
        priceStatRepository.upsert(
            pharmacyId, drugId,
            aggregate.getRepPrice(), aggregate.getMinPrice(), aggregate.getMaxPrice(), aggregate.getAvgPrice(),
            aggregate.getReportCount(), lastReportedAt, (short) windowDays);

        return priceStatRepository.findByPharmacyIdAndDrugId(pharmacyId, drugId);
    }
}
