package com.pharmaprice.recommendation.controller;

import com.pharmaprice.recommendation.dto.SearchResponse;
import com.pharmaprice.recommendation.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code docs/API.md} §5. 이 서비스의 핵심 엔드포인트 — 위치 + 약품 → 최저가 추천.
 *
 * <p>{@code DrugController}와 같은 이유로 {@code SecurityConfig}가 아직 없어(T-23
 * 몫) 실행 중인 서버는 이 엔드포인트도 401을 반환한다. 파라미터 파싱·위임만
 * 담당하고, 검증·조립은 전부 {@link SearchService}(T-15 본체)에 있다.</p>
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public SearchResponse search(
        @RequestParam Long drugId,
        @RequestParam(required = false) Double lat,
        @RequestParam(required = false) Double lng,
        @RequestParam(required = false) String regionCode,
        @RequestParam(defaultValue = "2000") int radius,
        @RequestParam(defaultValue = "SCORE") String sort,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return searchService.search(drugId, lat, lng, regionCode, radius, sort, limit);
    }
}
