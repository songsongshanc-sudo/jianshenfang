package com.gym.self.modules.user.auth;

public record MpPrincipal(long userId, String kind) {

    public boolean formal() {
        return "access".equals(kind);
    }

    public boolean faceEnroll() {
        return "face".equals(kind);
    }
}
