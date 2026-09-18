package com.pharmaprice.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * {@code docs/API.md} §6 {@code POST /price-reports} 요청.
 *
 * <p>{@code purchasedAt}에는 검증 어노테이션을 걸지 않는다 - 미입력 시 오늘로 채우고
 * 미래·180일 초과 과거를 거부하는 규칙은 "오늘"이 기준이라 {@code @PastOrPresent}
 * 만으로 표현할 수 없다. 서비스 계층에서 처리한다.</p>
 */
public record PriceReportCreateRequest(
    @NotNull Long pharmacyId,
    @NotNull Long drugId,
    @NotNull
    @Min(value = 100, message = "가격은 100원 이상 200,000원 이하여야 합니다.")
    @Max(value = 200_000, message = "가격은 100원 이상 200,000원 이하여야 합니다.")
    Integer price,
    LocalDate purchasedAt,
    Long receiptFileId,
    @Size(max = 200) String memo
) {
}
