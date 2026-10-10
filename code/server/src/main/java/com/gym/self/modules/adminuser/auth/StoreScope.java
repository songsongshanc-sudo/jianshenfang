package com.gym.self.modules.adminuser.auth;

import com.gym.self.common.api.BizException;

public final class StoreScope {

    private StoreScope() {
    }

    public static Long requiredStore(AdminPrincipal principal, Long requestedStoreId) {
        if (!principal.master()) {
            if (requestedStoreId != null && !requestedStoreId.equals(principal.storeId())) {
                throw BizException.forbidden("不能查看其他门店");
            }
            return principal.storeId();
        }
        return requestedStoreId;
    }

    public static void requireMaster(AdminPrincipal principal) {
        if (!principal.master()) {
            throw BizException.forbidden("只有总账号可以操作");
        }
    }

    public static void requireStoreAccess(AdminPrincipal principal, long storeId) {
        if (!principal.master() && (principal.storeId() == null || !principal.storeId().equals(storeId))) {
            throw BizException.forbidden("不能操作其他门店");
        }
    }
}
