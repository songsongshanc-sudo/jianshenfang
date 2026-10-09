package com.gym.self.modules.adminuser.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.modules.adminuser.auth.AdminAuthService;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminAccountController {

    private final AdminAuthService adminAuthService;

    public AdminAccountController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/auth/login")
    public ApiResponse<LoginView> login(@Valid @RequestBody LoginRequest request) {
        AdminAuthService.LoginResult result = adminAuthService.login(request.username(), request.password());
        return ApiResponse.ok(new LoginView(
                result.accessToken(),
                result.refreshToken(),
                result.role(),
                result.storeId() == null ? null : String.valueOf(result.storeId())));
    }

    @PostMapping("/accounts")
    public ApiResponse<IdView> create(@Valid @RequestBody CreateAccountRequest request) {
        String id = adminAuthService.createStoreAccount(
                CurrentAdmin.get(), request.username(), request.password(), Long.parseLong(request.storeId()));
        return ApiResponse.ok(new IdView(id));
    }

    @GetMapping("/accounts")
    public ApiResponse<List<AdminAuthService.AccountView>> list() {
        return ApiResponse.ok(adminAuthService.list(CurrentAdmin.get()));
    }

    @PostMapping("/accounts/{id}/password")
    public ApiResponse<PasswordChanged> password(@PathVariable String id, @Valid @RequestBody PasswordRequest request) {
        boolean self = adminAuthService.changePassword(CurrentAdmin.get(), Long.parseLong(id), request.password());
        return ApiResponse.ok(new PasswordChanged(self));
    }

    @DeleteMapping("/accounts/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        adminAuthService.delete(CurrentAdmin.get(), Long.parseLong(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/accounts/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable String id) {
        adminAuthService.disable(CurrentAdmin.get(), Long.parseLong(id));
        return ApiResponse.ok(null);
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginView(String accessToken, String refreshToken, String role, String storeId) {
    }

    public record CreateAccountRequest(@NotBlank String username, @NotBlank String password, @NotNull String storeId) {
    }

    public record IdView(String id) {
    }

    public record PasswordRequest(@NotBlank @Size(min = 6, max = 64) String password) {
    }

    public record PasswordChanged(boolean self) {
    }
}
