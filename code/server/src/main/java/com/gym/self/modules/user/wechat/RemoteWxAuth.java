package com.gym.self.modules.user.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.gym.self.common.api.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("prod")
public class RemoteWxAuth implements WxAuthPort {

    private static final Logger log = LoggerFactory.getLogger(RemoteWxAuth.class);

    private final WxMpAccessToken wxMpAccessToken;

    public RemoteWxAuth(WxMpAccessToken wxMpAccessToken) {
        this.wxMpAccessToken = wxMpAccessToken;
    }

    @Override
    public Session exchange(String code) {
        wxMpAccessToken.ensureConfigured();
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("微信登录凭证不正确");
        }
        try {
            String body = wxMpAccessToken.client().get()
                    .uri(uri -> uri.path("/sns/jscode2session")
                            .queryParam("appid", wxMpAccessToken.appId())
                            .queryParam("secret", wxMpAccessToken.appSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = wxMpAccessToken.read(body);
            String err = WxMpAccessToken.text(root, "errcode");
            if (err != null && !"0".equals(err)) {
                log.warn("jscode2session failed: {} {}", err, WxMpAccessToken.text(root, "errmsg"));
                throw BizException.rejected("微信登录失败，请稍后重试");
            }
            String openid = WxMpAccessToken.text(root, "openid");
            if (openid == null || openid.isBlank()) {
                throw BizException.rejected("微信登录失败，请稍后重试");
            }
            String sessionKey = WxMpAccessToken.text(root, "session_key");
            return new Session(openid, sessionKey == null ? "" : sessionKey);
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("jscode2session error", exception);
            throw BizException.rejected("微信登录失败，请稍后重试");
        }
    }

    @Override
    public String phone(String phoneCode) {
        wxMpAccessToken.ensureConfigured();
        if (phoneCode == null || phoneCode.isBlank()) {
            throw BizException.badRequest("手机号凭证不正确");
        }
        try {
            String body = wxMpAccessToken.client().post()
                    .uri(uri -> uri.path("/wxa/business/getuserphonenumber")
                            .queryParam("access_token", wxMpAccessToken.get())
                            .build())
                    .body(Map.of("code", phoneCode))
                    .retrieve()
                    .body(String.class);
            JsonNode root = wxMpAccessToken.read(body);
            int err = root.path("errcode").asInt(0);
            if (err == 40001) {
                wxMpAccessToken.invalidate();
                body = wxMpAccessToken.client().post()
                        .uri(uri -> uri.path("/wxa/business/getuserphonenumber")
                                .queryParam("access_token", wxMpAccessToken.get())
                                .build())
                        .body(Map.of("code", phoneCode))
                        .retrieve()
                        .body(String.class);
                root = wxMpAccessToken.read(body);
                err = root.path("errcode").asInt(0);
            }
            if (err != 0) {
                log.warn("getuserphonenumber failed: {} {}", err, WxMpAccessToken.text(root, "errmsg"));
                throw BizException.rejected("获取手机号失败，请稍后重试");
            }
            String phone = WxMpAccessToken.text(root.path("phone_info"), "purePhoneNumber");
            if (phone == null || !phone.matches("1\\d{10}")) {
                throw BizException.rejected("获取手机号失败，请稍后重试");
            }
            return phone;
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("getuserphonenumber error", exception);
            throw BizException.rejected("获取手机号失败，请稍后重试");
        }
    }
}
