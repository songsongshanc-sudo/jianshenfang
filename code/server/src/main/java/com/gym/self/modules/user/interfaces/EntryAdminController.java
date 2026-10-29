package com.gym.self.modules.user.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.user.application.EntryAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class EntryAdminController {

    private final EntryAdminService entryAdminService;

    public EntryAdminController(EntryAdminService entryAdminService) {
        this.entryAdminService = entryAdminService;
    }

    @GetMapping("/api/admin/entry-admins")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(entryAdminService.list(CurrentAdmin.get()));
    }

    @PostMapping("/api/admin/entry-admins")
    public ApiResponse<IdView> create(@Valid @RequestBody SaveRequest request) {
        String id = entryAdminService.create(CurrentAdmin.get(), Long.parseLong(request.storeId()),
                request.name(), request.phone(), request.faceBase64(), request.gateUntil());
        return ApiResponse.ok(new IdView(id));
    }

    @PutMapping("/api/admin/entry-admins/{id}")
    public ApiResponse<Void> update(@PathVariable String id, @Valid @RequestBody UpdateRequest request) {
        entryAdminService.update(CurrentAdmin.get(), Long.parseLong(id),
                request.name(), request.phone(), request.faceBase64(), request.gateUntil());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/entry-admins/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        entryAdminService.delete(CurrentAdmin.get(), Long.parseLong(id));
        return ApiResponse.ok(null);
    }

    public record SaveRequest(@NotBlank String storeId, @NotBlank String name, @NotBlank String phone,
                              @NotBlank String faceBase64, @NotBlank String gateUntil) {
    }

    public record UpdateRequest(@NotBlank String name, @NotBlank String phone, String faceBase64,
                                @NotBlank String gateUntil) {
    }

    public record IdView(String id) {
    }
}
