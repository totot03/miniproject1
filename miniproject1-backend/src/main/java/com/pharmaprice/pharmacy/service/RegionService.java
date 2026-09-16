package com.pharmaprice.pharmacy.service;

import com.pharmaprice.pharmacy.dto.RegionGroupResponse;
import java.util.List;

public interface RegionService {

    /** 전체 지역을 시도별로 그룹핑해 반환한다. 페이지네이션 없음. */
    List<RegionGroupResponse> list();
}
