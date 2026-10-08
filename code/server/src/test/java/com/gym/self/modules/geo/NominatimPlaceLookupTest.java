package com.gym.self.modules.geo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.api.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NominatimPlaceLookupTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reverseReadsProvinceCityAndStreet() {
        String json = """
                {"lat":"22.54","lon":"114.05","address":{"road":"深南大道","suburb":"粤海街道","city":"深圳市","state":"广东省"}}
                """;
        PlaceLookup.LookupHit hit = NominatimPlaceLookup.parseReverse(json, objectMapper);
        assertEquals("广东省", hit.province());
        assertEquals("深圳市", hit.city());
        assertEquals("粤海街道深南大道", hit.address());
    }

    @Test
    void districtTaggedAsCityFallsBackToPrefecture() {
        String json = """
                {"lat":"22.52","lon":"113.94","display_name":"深圳湾体育中心, 3001, 滨海大道, 粤海街道, 南山区, 深圳市, 广东省, 中国","address":{"house_number":"3001","road":"滨海大道","suburb":"粤海街道","city":"南山区","state":"广东省"}}
                """;
        PlaceLookup.LookupHit hit = NominatimPlaceLookup.parseReverse(json, objectMapper);
        assertEquals("广东省", hit.province());
        assertEquals("深圳市", hit.city());
        assertEquals("南山区粤海街道滨海大道3001", hit.address());
    }

    @Test
    void municipalityUsesProvinceAsCity() {
        String json = """
                {"lat":"39.9","lon":"116.4","address":{"road":"长安街","state":"北京市"}}
                """;
        PlaceLookup.LookupHit hit = NominatimPlaceLookup.parseReverse(json, objectMapper);
        assertEquals("北京市", hit.province());
        assertEquals("北京市", hit.city());
        assertEquals("长安街", hit.address());
    }

    @Test
    void missingPlaceIsRejected() {
        assertThrows(BizException.class, () -> NominatimPlaceLookup.parseReverse("{\"error\":\"Unable to geocode\"}", objectMapper));
    }
}
