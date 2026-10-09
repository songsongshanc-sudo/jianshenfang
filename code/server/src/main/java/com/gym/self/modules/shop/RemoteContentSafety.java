package com.gym.self.modules.shop;

import com.gym.self.common.api.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class RemoteContentSafety implements ContentSafety {

    @Override
    public void check(String text) {
        throw BizException.rejected("内容安全尚未配置");
    }
}
