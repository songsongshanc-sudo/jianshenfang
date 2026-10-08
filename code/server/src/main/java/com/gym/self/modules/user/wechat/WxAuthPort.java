package com.gym.self.modules.user.wechat;

public interface WxAuthPort {

    Session exchange(String code);

    String phone(String phoneCode);

    record Session(String openid, String sessionKey) {
    }
}
