package com.gym.self.modules.file;

import com.gym.self.common.api.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class CosPublicImageStorage implements PublicImageStorage {

    @Override
    public String store(String biz, String contentType, byte[] body) {
        throw BizException.badRequest("正式环境的文件要上传到对象存储，服务商还没选定，当前不能上传");
    }
}
