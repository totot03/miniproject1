package com.pharmaprice.pharmacy.controller;

import com.pharmaprice.pharmacy.dto.RegionGroupResponse;
import com.pharmaprice.pharmacy.service.RegionService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code docs/API.md} §7 지역 목록. 위치 권한 거부 시 프론트 폴백 드롭다운용이다.
 *
 * <p>전체 ~250건으로 페이지네이션이 없고({@code docs/ROADMAP.md} T-14), 자주
 * 바뀌지 않는 데이터라 {@code Cache-Control: max-age=3600}을 붙인다.</p>
 */
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    public ResponseEntity<List<RegionGroupResponse>> list() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
            .body(regionService.list());
    }
}
