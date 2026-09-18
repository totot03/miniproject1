package com.pharmaprice.report.controller;

import com.pharmaprice.auth.security.AuthPrincipal;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.report.dto.PriceReportCreateRequest;
import com.pharmaprice.report.dto.PriceReportListItemResponse;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.service.PriceReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code docs/API.md} §6 {@code POST}/{@code GET /price-reports}.
 *
 * <p>{@code GET}은 {@code SecurityConfig}에서 permitAll이지만, {@code mine=true}는
 * API.md상 🔐라 여기서만 조건부로 인증을 강제한다 - {@code SecurityConfig}의
 * {@code anyRequest().authenticated()}로는 "특정 쿼리 파라미터일 때만 인증 필요"를
 * 표현할 수 없다. {@link InsufficientAuthenticationException}(Spring Security
 * 표준 인증 예외)을 던지면 {@code ExceptionTranslationFilter}가 필터체인 실행
 * 안에서 이를 가로채 기존 {@code JwtAuthenticationEntryPoint}로 넘겨준다 - 새
 * 예외 핸들러 없이 POST와 동일한 401 포맷이 그대로 재사용된다.</p>
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

    @GetMapping
    public PageResponse<PriceReportListItemResponse> list(
        @AuthenticationPrincipal AuthPrincipal principal,
        @RequestParam(required = false) Long pharmacyId,
        @RequestParam(required = false) Long drugId,
        @RequestParam(defaultValue = "false") boolean mine,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (mine && principal == null) {
            throw new InsufficientAuthenticationException("인증이 필요합니다.");
        }
        Long userId = mine ? principal.userId() : null;
        return priceReportService.list(pharmacyId, drugId, userId, page, size);
    }
}
