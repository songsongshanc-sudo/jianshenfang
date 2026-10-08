package com.gym.self.modules.adminuser.auth;

import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentAdmin {

    private CurrentAdmin() {
    }

    public static AdminPrincipal get() {
        return (AdminPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
