package com.gym.self.modules.user.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.api.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Profile("prod")
public class WxMpAccessToken {

    private static final Logger log = LoggerFactory.getLogger(WxMpAccessToken.class);

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String appId;
    private final String appSecret;
    private final AtomicReference<CachedToken> token = new AtomicReference<>();

    public WxMpAccessToken(
            ObjectMapper objectMapper,
            @Value("${gym.wx.mp-appid:}") String appId,
            @Value("${gym.wx.mp-secret:}") String appSecret) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = RestClient.builder()
                .baseUrl("https://api.weixin.qq.com")
                .requestFactory(factory)
                .build();
        this.objectMapper = objectMapper;
        this.appId = trim(appId);
        this.appSecret = trim(appSecret);
    }

    public RestClient client() {
        return client;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public void ensureConfigured() {
        if (appId.isEmpty() || appSecret.isEmpty()) {
            throw BizException.badRequest("微信小程序尚未配置");
        }
    }

    public boolean configured() {
        return !appId.isEmpty() && !appSecret.isEmpty();
    }

    public String appId() {
        return appId;
    }

    public String appSecret() {
        return appSecret;
    }

    public String get() {
        ensureConfigured();
        CachedToken cached = token.get();
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAtMs > now + 60_000) {
            return cached.value;
        }
        synchronized (this) {
            cached = token.get();
            now = System.currentTimeMillis();
            if (cached != null && cached.expireAtMs > now + 60_000) {
                return cached.value;
            }
            String body = client.get()
                    .uri(uri -> uri.path("/cgi-bin/token")
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", appId)
                            .queryParam("secret", appSecret)
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = read(body);
            String accessToken = text(root, "access_token");
            int expiresIn = root.path("expires_in").asInt(0);
            if (accessToken == null || accessToken.isBlank() || expiresIn <= 0) {
                log.warn("access_token failed: {}", body);
                throw BizException.badRequest("微信小程序尚未配置");
            }
            token.set(new CachedToken(accessToken, now + expiresIn * 1000L));
            return accessToken;
        }
    }

    public void invalidate() {
        token.set(null);
    }

    public JsonNode read(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception exception) {
            throw BizException.rejected("微信接口返回不正确");
        }
    }

    public static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private record CachedToken(String value, long expireAtMs) {
    }
}
