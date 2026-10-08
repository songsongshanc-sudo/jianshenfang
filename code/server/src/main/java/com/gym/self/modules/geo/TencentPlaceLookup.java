package com.gym.self.modules.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.api.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("prod")
public class TencentPlaceLookup implements PlaceLookup {

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String key;

    public TencentPlaceLookup(ObjectMapper objectMapper, @Value("${gym.map.tencent-key:}") String key) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder()
                .baseUrl("https://apis.map.qq.com")
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
        this.key = key == null ? "" : key;
    }

    @Override
    public LookupHit reverse(double latitude, double longitude) {
        requireKey();
        String body = get(uri -> uri.path("/ws/geocoder/v1/")
                .queryParam("location", latitude + "," + longitude)
                .queryParam("key", key));
        return parseReverse(body, objectMapper);
    }

    @Override
    public List<LookupHit> search(String keyword) {
        requireKey();
        String body = get(uri -> uri.path("/ws/place/v1/suggestion")
                .queryParam("keyword", keyword)
                .queryParam("key", key));
        return parseSearch(body, objectMapper);
    }

    @Override
    public boolean coordinatesAreWgs84() {
        return false;
    }

    static LookupHit parseReverse(String body, ObjectMapper objectMapper) {
        JsonNode root = read(body, objectMapper);
        ensureOk(root);
        JsonNode result = root.path("result");
        JsonNode component = result.path("address_component");
        String province = text(component, "province");
        String city = text(component, "city");
        if (city == null) {
            city = province;
        }
        if (province == null || city == null) {
            throw BizException.badRequest("没有解析出省市，请换一个位置或手填");
        }
        String recommend = text(result.path("formatted_addresses"), "recommend");
        String street = recommend != null ? recommend : join(
                text(component, "district"),
                text(component, "street"),
                text(component, "street_number"));
        JsonNode location = result.path("location");
        return new LookupHit(province, city, street, location.path("lat").asDouble(), location.path("lng").asDouble());
    }

    static List<LookupHit> parseSearch(String body, ObjectMapper objectMapper) {
        JsonNode root = read(body, objectMapper);
        ensureOk(root);
        List<LookupHit> hits = new ArrayList<>();
        for (JsonNode node : root.path("data")) {
            String province = text(node, "province");
            String city = text(node, "city");
            if (city == null) {
                city = province;
            }
            if (province == null || city == null) {
                continue;
            }
            JsonNode location = node.path("location");
            String address = join(text(node, "district"), text(node, "title"), text(node, "address"));
            hits.add(new LookupHit(province, city, address, location.path("lat").asDouble(), location.path("lng").asDouble()));
        }
        return hits;
    }

    private void requireKey() {
        if (key.isBlank()) {
            throw BizException.badRequest("正式环境请配置腾讯位置服务 Key，当前无法解析地址");
        }
    }

    private String get(java.util.function.Function<org.springframework.web.util.UriBuilder, org.springframework.web.util.UriBuilder> query) {
        try {
            return client.get().uri(uri -> query.apply(uri).build()).retrieve().body(String.class);
        } catch (RestClientException exception) {
            throw BizException.badRequest("地址解析失败，请稍后再试");
        }
    }

    private static void ensureOk(JsonNode root) {
        if (root.path("status").asInt(-1) != 0) {
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

    private static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank() && !builder.toString().contains(part)) {
                builder.append(part);
            }
        }
        return builder.toString();
    }

    private static String text(JsonNode node, String name) {
        String value = node.path(name).asText("");
        return value.isBlank() ? null : value;
    }
}
