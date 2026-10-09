package com.gym.self.modules.user.face;

public interface FaceVendorPort {

    Result enroll(long userId, String objectKey, long sizeBytes);

    void revoke(String vendorFaceId);

    record Result(boolean enrolled, String vendorFaceId, String reason) {
    }
}
