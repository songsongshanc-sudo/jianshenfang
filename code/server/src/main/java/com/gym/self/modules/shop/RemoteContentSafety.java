package com.gym.self.modules.shop;

import com.fasterxml.jackson.databind.JsonNode;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.user.domain.GymUser;
import com.gym.self.modules.user.domain.GymUserMapper;
import com.gym.self.modules.user.wechat.WxMpAccessToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Profile("prod")
public class RemoteContentSafety implements ContentSafety {

    private static final Logger log = LoggerFactory.getLogger(RemoteContentSafety.class);
    private static final int MAX_CHARS = 2500;
    /** 报修/投诉/失物按评论类场景 */
    private static final int SCENE_COMMENT = 2;

    private final WxMpAccessToken wxMpAccessToken;
    private final GymUserMapper gymUserMapper;

    public RemoteContentSafety(WxMpAccessToken wxMpAccessToken, GymUserMapper gymUserMapper) {
        this.wxMpAccessToken = wxMpAccessToken;
        this.gymUserMapper = gymUserMapper;
    }

    @Override
    public void check(long userId, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!wxMpAccessToken.configured()) {
            throw BizException.badRequest("内容安全尚未配置");
        }
        GymUser user = gymUserMapper.selectById(userId);
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            throw BizException.badRequest("请重新登录后再提交");
        }
        String openid = user.getOpenid();
        String content = text.trim();
        for (int start = 0; start < content.length(); start += MAX_CHARS) {
            int end = Math.min(start + MAX_CHARS, content.length());
            checkChunk(openid, content.substring(start, end));
        }
    }

    private void checkChunk(String openid, String content) {
        JsonNode root = call(openid, content, true);
        int err = root.path("errcode").asInt(0);
        if (err != 0) {
            if (err == 61010) {
                throw BizException.rejected("请重新打开小程序后再提交");
            }
            if (err == 40003 || err == 43104) {
                throw BizException.badRequest("请重新登录后再提交");
            }
            log.warn("msg_sec_check failed: {} {}", err, WxMpAccessToken.text(root, "errmsg"));
            throw BizException.rejected("内容安全检查失败，请稍后重试");
        }
        String suggest = WxMpAccessToken.text(root.path("result"), "suggest");
        if (suggest == null || "pass".equalsIgnoreCase(suggest)) {
            return;
        }
        throw BizException.rejected("内容未通过安全检查");
    }

    private JsonNode call(String openid, String content, boolean retryOnToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", 2);
        body.put("openid", openid);
        body.put("scene", SCENE_COMMENT);
        body.put("content", content);
        try {
            String response = wxMpAccessToken.client().post()
                    .uri(uri -> uri.path("/wxa/msg_sec_check")
                            .queryParam("access_token", wxMpAccessToken.get())
                            .build())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = wxMpAccessToken.read(response);
            if (retryOnToken && root.path("errcode").asInt(0) == 40001) {
                wxMpAccessToken.invalidate();
                return call(openid, content, false);
            }
            return root;
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("msg_sec_check error", exception);
            throw BizException.rejected("内容安全检查失败，请稍后重试");
        }
    }
}
