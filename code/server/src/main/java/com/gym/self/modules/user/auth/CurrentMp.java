package com.gym.self.modules.user.auth;

import com.gym.self.common.api.BizException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentMp {

    private CurrentMp() {
    }

    public static MpPrincipal get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof MpPrincipal mp) {
            return mp;
        }
        throw BizException.unauthorized();
    }

    public static MpPrincipal formal() {
        MpPrincipal principal = get();
        if (!principal.formal()) {
            throw BizException.unauthorized();
        }
        return principal;
    }

    public static Long optionalFormalUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof MpPrincipal mp && mp.formal()) {
            return mp.userId();
        }
        return null;
    }
}
