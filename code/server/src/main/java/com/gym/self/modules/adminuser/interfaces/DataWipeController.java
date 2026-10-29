package com.gym.self.modules.adminuser.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.modules.adminuser.application.DataWipeService;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataWipeController {

    private final DataWipeService dataWipeService;

    public DataWipeController(DataWipeService dataWipeService) {
        this.dataWipeService = dataWipeService;
    }

    @PostMapping("/api/admin/system/wipe")
    public ApiResponse<Void> wipe() {
        dataWipeService.wipe(CurrentAdmin.get());
        return ApiResponse.ok(null);
    }
}
