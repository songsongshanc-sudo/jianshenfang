package com.gym.self.modules.store.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.store.application.StoreAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/stores")
public class StoreAdminController {

    private final StoreAdminService storeAdminService;

    public StoreAdminController(StoreAdminService storeAdminService) {
        this.storeAdminService = storeAdminService;
    }

    @PostMapping
    public ApiResponse<IdView> create(@Valid @RequestBody CreateStoreRequest request) {
        String id = storeAdminService.create(
                CurrentAdmin.get(),
                request.code(),
                request.name(),
                request.province(),
                request.city(),
                request.address(),
                request.longitude(),
                request.latitude());
        return ApiResponse.ok(new IdView(id));
    }

    @GetMapping
    public ApiResponse<List<StoreAdminService.StoreView>> list(@RequestParam(required = false) String storeId) {
        Long requested = storeId == null || storeId.isBlank() ? null : parseId(storeId);
        return ApiResponse.ok(storeAdminService.list(CurrentAdmin.get(), requested));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable String id, @Valid @RequestBody UpdateStoreRequest request) {
        storeAdminService.update(CurrentAdmin.get(), parseId(id), new StoreAdminService.StoreUpdate(
                request.name(), request.province(), request.city(), request.address(), request.longitude(),
                request.latitude(), request.coverUrl(), request.businessHours(), request.status(),
                request.wifiSsid(), request.wifiPassword()));
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/phones")
    public ApiResponse<List<StoreAdminService.PhoneView>> phones(@PathVariable String id) {
        return ApiResponse.ok(storeAdminService.phones(CurrentAdmin.get(), parseId(id)));
    }

    @PutMapping("/{id}/phones")
    public ApiResponse<Void> replacePhones(@PathVariable String id, @Valid @RequestBody ReplacePhonesRequest request) {
        storeAdminService.replacePhones(CurrentAdmin.get(), parseId(id), request.items().stream()
                .map(item -> new StoreAdminService.PhoneInput(item.phoneType(), item.phone(), item.timeStart(),
                        item.timeEnd(), item.sortNo()))
                .toList());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/guides")
    public ApiResponse<List<StoreAdminService.GuideView>> guides(@PathVariable String id) {
        return ApiResponse.ok(storeAdminService.guides(CurrentAdmin.get(), parseId(id)));
    }

    @PutMapping("/{id}/guides")
    public ApiResponse<Void> replaceGuides(@PathVariable String id, @Valid @RequestBody ReplaceGuidesRequest request) {
        storeAdminService.replaceGuides(CurrentAdmin.get(), parseId(id), request.items().stream()
                .map(item -> new StoreAdminService.GuideInput(item.imageUrl(), item.caption(), item.sortNo()))
                .toList());
        return ApiResponse.ok(null);
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("门店不存在");
        }
    }

    public record CreateStoreRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String province,
            @NotBlank String city,
            @NotBlank String address,
            @NotNull BigDecimal longitude,
            @NotNull BigDecimal latitude) {
    }

    public record UpdateStoreRequest(
            @NotBlank String name,
            @NotBlank String province,
            @NotBlank String city,
            @NotBlank String address,
            @NotNull BigDecimal longitude,
            @NotNull BigDecimal latitude,
            @Size(max = 512) String coverUrl,
            @Size(max = 32) String businessHours,
            @NotBlank String status,
            @Size(max = 64) String wifiSsid,
            @Size(max = 64) String wifiPassword) {
    }

    public record PhoneItem(
            @NotBlank String phoneType,
            @NotBlank @Size(max = 20) String phone,
            String timeStart,
            String timeEnd,
            Integer sortNo) {
    }

    public record ReplacePhonesRequest(@NotNull @Size(max = 20) @Valid List<PhoneItem> items) {
    }

    public record GuideItem(
            @NotBlank @Size(max = 512) String imageUrl,
            @NotBlank @Size(max = 255) String caption,
            Integer sortNo) {
    }

    public record ReplaceGuidesRequest(@NotNull @Size(max = 30) @Valid List<GuideItem> items) {
    }

    public record IdView(String id) {
    }
}
