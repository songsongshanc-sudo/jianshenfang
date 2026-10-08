package com.gym.self.modules.user.wechat;

import com.gym.self.common.api.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local & !test")
public class RemoteWxAuth implements WxAuthPort {

    @Override
    public Session exchange(String code) {
        throw BizException.badRequest("微信小程序尚未配置");
    }

    @Override
    public String phone(String phoneCode) {
        throw BizException.badRequest("微信小程序尚未配置");
    }
}
