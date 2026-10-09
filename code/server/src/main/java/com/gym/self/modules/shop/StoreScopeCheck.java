package com.gym.self.modules.shop;

import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.StoreScope;

final class StoreScopeCheck {
    private StoreScopeCheck() {
    }

    static void masterOrOwn(AdminPrincipal actor, long storeId) {
        StoreScope.requiredStore(actor, storeId);
    }
}
