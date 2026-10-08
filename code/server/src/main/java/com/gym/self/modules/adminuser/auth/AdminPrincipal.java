package com.gym.self.modules.adminuser.auth;

public record AdminPrincipal(long id, String role, Long storeId) {

    public boolean master() {
        return "MASTER".equals(role);
    }
}
