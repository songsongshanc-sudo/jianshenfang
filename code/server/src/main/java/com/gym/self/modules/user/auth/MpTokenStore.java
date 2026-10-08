package com.gym.self.modules.user.auth;

public interface MpTokenStore {

    void save(long userId, String jti, long ttlSeconds);

    boolean valid(String jti);

    void revoke(String jti);
}
