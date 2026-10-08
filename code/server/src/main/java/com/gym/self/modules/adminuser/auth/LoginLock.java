package com.gym.self.modules.adminuser.auth;

public interface LoginLock {

    boolean locked(String username);

    void recordFailure(String username);

    void clear(String username);
}
