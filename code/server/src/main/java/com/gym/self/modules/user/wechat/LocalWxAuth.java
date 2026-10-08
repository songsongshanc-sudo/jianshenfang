package com.gym.self.modules.user.wechat;

import com.gym.self.common.api.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class LocalWxAuth implements WxAuthPort {

    @Override
    public Session exchange(String code) {
        String cleaned = code == null ? "" : code.replaceAll("[^A-Za-z0-9]", "");
        if (cleaned.length() < 4) {
            throw BizException.badRequest("微信登录凭证不正确");
        }
        String openid = "dev" + cleaned.substring(0, Math.min(cleaned.length(), 28));
        return new Session(openid, "local-session-" + openid);
    }

    @Override
    public String phone(String phoneCode) {
        if (phoneCode != null && phoneCode.matches("1\\d{10}")) {
            return phoneCode;
        }
        throw BizException.badRequest("本地测试请填写 11 位手机号");
    }
}
