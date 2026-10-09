package com.gym.self.modules.user.face;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile({"local", "dev", "test"})
public class MockFaceVendor implements FaceVendorPort {

    @Override
    public Result enroll(long userId, String objectKey, long sizeBytes) {
        if (sizeBytes < 32) {
            return new Result(false, null, "未检测到正脸，请重新拍摄");
        }
        return new Result(true, "mock-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8), null);
    }

    @Override
    public void revoke(String vendorFaceId) {
    }
}
