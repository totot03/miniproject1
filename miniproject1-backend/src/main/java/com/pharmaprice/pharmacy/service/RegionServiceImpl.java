package com.pharmaprice.pharmacy.service;

import com.pharmaprice.pharmacy.dto.RegionGroupResponse;
import com.pharmaprice.pharmacy.dto.RegionGroupResponse.SigunguItem;
import com.pharmaprice.pharmacy.repository.RegionQueryRepository;
import com.pharmaprice.pharmacy.repository.RegionQueryRepository.RegionRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private final RegionQueryRepository regionQueryRepository;

    @Override
    public List<RegionGroupResponse> list() {
        List<RegionRow> rows = regionQueryRepository.listWithPharmacyCount();

        // 리포지토리 쿼리가 이미 sido, sigungu 순으로 정렬해 내려준다. groupingBy의
        // 기본 맵 구현(HashMap)을 쓰면 이 순서가 해시 기반으로 흐트러지므로,
        // LinkedHashMap을 명시해 원본(=정렬된) 순서를 그대로 보존한다.
        Map<String, List<RegionRow>> bySido = rows.stream()
            .collect(Collectors.groupingBy(RegionRow::getSido, LinkedHashMap::new, Collectors.toList()));

        return bySido.entrySet().stream()
            .map(entry -> new RegionGroupResponse(entry.getKey(), toSigungus(entry.getValue())))
            .toList();
    }

    private List<SigunguItem> toSigungus(List<RegionRow> rows) {
        return rows.stream()
            .map(row -> new SigunguItem(
                row.getCode(), row.getSigungu(), row.getCenterLat(), row.getCenterLng(), row.getPharmacyCount()))
            .toList();
    }
}
