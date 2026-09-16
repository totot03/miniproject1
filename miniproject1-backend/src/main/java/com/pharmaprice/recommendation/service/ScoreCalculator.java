package com.pharmaprice.recommendation.service;

import com.pharmaprice.recommendation.dto.Badge;
import com.pharmaprice.recommendation.dto.ScoreBreakdown;
import java.time.LocalDate;
import java.util.List;

/**
 * 가격·거리·신선도 점수를 가중 합산해 후보 약국의 순위를 매긴다.
 * docs/API.md §5 "Score 계산 명세", docs/PRD.md §F3.2 를 그대로 구현한다.
 *
 * <p>DB/Spring 컨텍스트 없이 순수 Java 로직으로 테스트 가능해야 한다는
 * ROADMAP T-11 설계 원칙에 따라, 이 인터페이스는 이미 계산된 거리(distanceM)와
 * 통계(repPrice, lastReportedAt, reportCount)만 입력으로 받는다 — SQL이나
 * {@link com.pharmaprice.recommendation.distance.DistanceCalculator} 를 직접 호출하지 않는다.</p>
 */
public interface ScoreCalculator {

    /**
     * @param candidates 반경 R 내, 활성 약국이면서 해당 약품의 대표가격 통계가
     *                   존재하는 후보군(호출부가 이미 필터링을 완료한 것). 비어 있으면
     *                   빈 리스트를 반환한다.
     * @param radiusM    검색에 사용된 반경(미터). distanceScore 정규화 기준(R).
     * @param today      freshnessScore 계산 기준일. 테스트에서 고정 날짜를
     *                   주입할 수 있도록 파라미터로 분리한다(내부에서 LocalDate.now() 호출 금지).
     * @return score DESC → repPrice ASC → distanceM ASC → pharmacyId ASC 로 정렬된 결과
     */
    List<ScoredCandidate> rank(List<Candidate> candidates, int radiusM, LocalDate today);

    /** 순위 계산 입력 1건. 이미 반경/활성/통계 존재 필터를 통과한 후보다. */
    record Candidate(
        long pharmacyId,
        int repPrice,
        double distanceM,
        LocalDate lastReportedAt,
        int reportCount
    ) {}

    /** 순위 계산 결과 1건. score와 breakdown은 이미 소수점 4자리로 반올림돼 있다. */
    record ScoredCandidate(
        Candidate candidate,
        double score,
        ScoreBreakdown breakdown,
        List<Badge> badges
    ) {}
}
