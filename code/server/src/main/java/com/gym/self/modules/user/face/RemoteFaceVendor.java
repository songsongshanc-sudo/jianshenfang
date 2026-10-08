package com.gym.self.modules.user.face;

import com.gym.self.common.api.BizException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class RemoteFaceVendor implements FaceVendorPort {

    @Override
    public Result enroll(long userId, String objectKey, long sizeBytes) {
        throw BizException.badRequest("人脸厂商尚未配置");
    }
}
