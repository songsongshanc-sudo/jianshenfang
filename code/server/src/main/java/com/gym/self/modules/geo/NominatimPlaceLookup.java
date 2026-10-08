package com.gym.self.modules.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.api.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile({"local", "dev", "test"})
public class NominatimPlaceLookup implements PlaceLookup {

    private final RestClient client;
    private final ObjectMapper objectMapper;

    public NominatimPlaceLookup(ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .requestFactory(factory)
                .defaultHeader("User-Agent", "gym-self-admin/1.0")
                .defaultHeader("Accept-Language", "zh-CN")
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public LookupHit reverse(double latitude, double longitude) {
        String body = get("/reverse", builder -> builder
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1));
        return parseReverse(body, objectMapper);
    }

    @Override
    public List<LookupHit> search(String keyword) {
        String body = get("/search", builder -> builder
                .queryParam("q", keyword)
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("limit", 5)
                .queryParam("countrycodes", "cn"));
        return parseSearch(body, objectMapper);
    }

    @Override
    public boolean coordinatesAreWgs84() {
        return true;
    }

    static LookupHit parseReverse(String body, ObjectMapper objectMapper) {
        JsonNode root = read(body, objectMapper);
        if (root.hasNonNull("error")) {
            throw BizException.badRequest("没有解析到这个位置，请换个点或手填");
        }
        return hit(root);
    }

    static List<LookupHit> parseSearch(String body, ObjectMapper objectMapper) {
        JsonNode root = read(body, objectMapper);
        List<LookupHit> hits = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode node : root) {
                hits.add(hit(node));
            }
        }
        return hits;
    }

    private String get(String path, java.util.function.Function<org.springframework.web.util.UriBuilder, org.springframework.web.util.UriBuilder> query) {
        try {
            return client.get().uri(uri -> query.apply(uri.path(path)).build()).retrieve().body(String.class);
        } catch (RestClientException exception) {
            throw BizException.badRequest("地址解析失败，请稍后再试");
        }
    }

    private static JsonNode read(String body, ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw BizException.badRequest("地址解析失败，请稍后再试");
        }
    }

    private static LookupHit hit(JsonNode node) {
        JsonNode address = node.path("address");
        String province = text(address, "state", "province", "region");
        String rawCity = text(address, "city", "town", "municipality");
        String district = text(address, "city_district", "district", "county");
        String city = rawCity;
        if (rawCity != null && rawCity.endsWith("区")) {
            district = rawCity;
            city = cityBeforeProvince(node.path("display_name").asText(""));
        }
        if (city == null) {
            city = province;
        }
        if (province == null || city == null) {
            throw BizException.badRequest("没有解析出省市，请换一个位置或手填");
        }
        String suburb = text(address, "suburb", "quarter");
        String road = text(address, "road", "pedestrian");
        String number = text(address, "house_number");
        String street = join(district, suburb, road, number);
        if (street.isBlank()) {
            street = node.path("name").asText("");
        }
        if (street.isBlank()) {
            street = node.path("display_name").asText("");
        }
        double latitude = node.path("lat").asDouble();
        double longitude = node.path("lon").asDouble();
        return new LookupHit(province, city, street, latitude, longitude);
    }

    private static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                builder.append(part);
            }
        }
        return builder.toString();
    }

    private static String cityBeforeProvince(String displayName) {
        String[] parts = displayName.split(",\\s*");
        for (int index = parts.length - 1; index >= 0; index--) {
            String part = parts[index].trim();
            if (part.endsWith("市")) {
                return part;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
