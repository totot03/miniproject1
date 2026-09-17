package com.pharmaprice.report.controller;

import com.pharmaprice.auth.security.AuthPrincipal;
import com.pharmaprice.report.dto.PriceReportCreateRequest;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.service.PriceReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code docs/API.md} §6 {@code POST /price-reports}. GET 목록 조회는
 * {@code docs/ROADMAP.md} T-26 카드에 없어 이번 범위에서 다루지 않는다.
 *
 * <p>{@code SecurityConfig}의 permitAll 목록에 이 경로가 없어
 * {@code anyRequest().authenticated()}에 자동으로 걸린다 - 별도 설정 불필요.</p>
 */
@RestController
@RequestMapping("/api/v1/price-reports")
@RequiredArgsConstructor
public class PriceReportController {

    private final PriceReportService priceReportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PriceReportResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody PriceReportCreateRequest request) {
        return priceReportService.create(principal.userId(), request);
    }
}
