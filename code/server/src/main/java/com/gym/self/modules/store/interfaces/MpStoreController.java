package com.gym.self.modules.store.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.store.application.StoreAdminService;
import com.gym.self.modules.user.auth.CurrentMp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/mp/stores")
public class MpStoreController {

    private final StoreAdminService storeAdminService;

    public MpStoreController(StoreAdminService storeAdminService) {
        this.storeAdminService = storeAdminService;
    }

    @GetMapping
    public ApiResponse<List<StoreAdminService.PublicStore>> list(
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal latitude) {
        return ApiResponse.ok(storeAdminService.publicList(province, city, longitude, latitude));
    }

    @GetMapping("/{id}")
    public ApiResponse<StoreAdminService.PublicStore> detail(
            @PathVariable String id,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) BigDecimal latitude) {
        return ApiResponse.ok(storeAdminService.publicDetail(parseId(id), longitude, latitude));
    }

    @GetMapping("/{id}/contacts")
    public ApiResponse<StoreAdminService.ContactsView> contacts(@PathVariable String id) {
        return ApiResponse.ok(storeAdminService.contacts(parseId(id)));
    }

    @GetMapping("/{id}/guides")
    public ApiResponse<List<StoreAdminService.GuideView>> guides(@PathVariable String id) {
        return ApiResponse.ok(storeAdminService.publicGuides(parseId(id)));
    }

    @GetMapping("/{id}/wifi")
    public ApiResponse<StoreAdminService.WifiView> wifi(@PathVariable String id) {
        Long userId = CurrentMp.optionalFormalUserId();
        if (userId == null) {
            throw BizException.unauthorized();
        }
        return ApiResponse.ok(storeAdminService.wifi(userId, parseId(id)));
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("门店不存在");
        }
    }
}
