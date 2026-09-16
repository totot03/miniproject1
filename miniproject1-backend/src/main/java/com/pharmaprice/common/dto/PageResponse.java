package com.pharmaprice.common.dto;

import java.util.List;

/**
 * {@code docs/API.md} §1.1 "목록 응답은 페이지네이션 래퍼를 쓴다"의 공통 구현.
 *
 * <p>특정 도메인(drug 등) 전용이 아니라, 목록을 반환하는 모든 API가 공유하는
 * 범용 타입이라 {@code common.dto}에 둔다 — API.md §1.1이 전역 규약으로
 * 못박아둬 사실상 모든 목록 API가 같은 응답 모양을 요구한다.</p>
 *
 * @param content       현재 페이지의 데이터
 * @param page          0-base 페이지 번호
 * @param size          페이지 크기(요청값, clamp 이후 값)
 * @param totalElements 전체 건수
 * @param totalPages    전체 페이지 수
 * @param hasNext       다음 페이지 존재 여부
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext
) {

    /**
     * 조회 결과로부터 페이지네이션 메타데이터(totalPages, hasNext)를 계산해 조립한다.
     * {@code size <= 0}이면(호출부 방어) totalPages는 0으로 둔다 — 나누기 예외 방지.
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean hasNext = (long) (page + 1) * size < totalElements;
        return new PageResponse<>(content, page, size, totalElements, totalPages, hasNext);
    }
}
