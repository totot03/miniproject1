package com.pharmaprice.drug.controller;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugSummaryResponse;
import com.pharmaprice.drug.service.DrugService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code docs/API.md} §3 의약품 검색/자동완성/상세.
 *
 * <p>이 프로젝트의 첫 컨트롤러다. {@code SecurityConfig}가 아직 없어(T-23
 * 몫) 실행 중인 서버는 기본 Spring Security 설정 때문에 이 엔드포인트도
 * 401을 반환한다 — 버그가 아니라 보안 배선이 T-23으로 분리된 결과이며,
 * 완료 판정은 {@code DrugControllerTest}(보안 오토컨피그 제외 슬라이스
 * 테스트)로 재현한다.</p>
 */
@RestController
@RequestMapping("/api/v1/drugs")
@RequiredArgsConstructor
public class DrugController {

    private final DrugService drugService;

    @GetMapping
    public PageResponse<DrugSummaryResponse> search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String category,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return drugService.search(q, category, page, size);
    }

    @GetMapping("/{drugId}")
    public DrugDetailResponse detail(@PathVariable Long drugId) {
        return drugService.getDetail(drugId);
    }
}
