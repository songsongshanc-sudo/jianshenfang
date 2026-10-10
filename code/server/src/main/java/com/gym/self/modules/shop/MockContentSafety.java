package com.gym.self.modules.shop;

import com.gym.self.common.api.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev", "test"})
public class MockContentSafety implements ContentSafety {

    @Override
    public void check(long userId, String text) {
        if (text != null && text.contains("违禁")) {
            throw BizException.rejected("内容未通过安全检查");
        }
    }
}
