package com.pharmaprice.support;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;

/**
 * 매핑 테스트가 공통으로 필요로 하는 FK 선행 데이터를 만드는 빌더 모음.
 *
 * <p>{@code price_report} 하나를 저장하려면 약국·약품(그리고 약국은 지역)이
 * 먼저 있어야 하므로, 각 테스트가 준비 코드를 반복해서 쓰지 않도록
 * 최소 필드만 채운 기본 인스턴스를 제공한다.</p>
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static Region region() {
        return Region.builder()
            .code("11680")
            .sido("서울특별시")
            .sigungu("강남구")
            .centerLat(37.4979)
            .centerLng(127.0276)
            .build();
    }

    public static Pharmacy pharmacy(Region region) {
        return Pharmacy.builder()
            .name("가온약국")
            .region(region)
            .lat(37.5012)
            .lng(127.0396)
            .build();
    }

    public static Drug drug() {
        return Drug.builder()
            .name("타이레놀정500밀리그람")
            .displayName("타이레놀 500mg")
            .category("해열진통")
            .packageUnit("8정")
            .build();
    }

    public static AppUser appUser(String email) {
        return AppUser.builder()
            .email(email)
            .passwordHash("$2a$10$dummyHashForTestOnly")
            .nickname("민지")
            .build();
    }
}
