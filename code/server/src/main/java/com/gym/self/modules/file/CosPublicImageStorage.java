package com.gym.self.modules.file;

import com.gym.self.common.api.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class CosPublicImageStorage implements PublicImageStorage {

    @Override
    public String store(String biz, String contentType, byte[] body) {
        throw BizException.badRequest("正式环境请把图片上传到对象存储，当前未配置 COS");
    }
}
