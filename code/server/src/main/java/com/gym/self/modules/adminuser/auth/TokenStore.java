package com.gym.self.modules.adminuser.auth;

public interface TokenStore {

    void save(long adminId, String jti, String kind, long ttlSeconds);

    boolean valid(String jti);

    void revokeAdmin(long adminId);
}
