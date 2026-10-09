package com.gym.self.modules.user.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.user.application.MpAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mp")
public class MpAuthController {

    private final MpAuthService mpAuthService;

    public MpAuthController(MpAuthService mpAuthService) {
        this.mpAuthService = mpAuthService;
    }

    @PostMapping("/auth/session")
    public ApiResponse<MpAuthService.SessionView> session(@Valid @RequestBody CodeRequest request) {
        return ApiResponse.ok(mpAuthService.session(request.code()));
    }

    @PostMapping("/auth/phone")
    public ApiResponse<MpAuthService.LoginView> phone(@Valid @RequestBody PhoneRequest request) {
        return ApiResponse.ok(mpAuthService.phone(request.phoneCode()));
    }

    @PostMapping("/auth/refresh")
    public ApiResponse<MpAuthService.LoginView> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(mpAuthService.refresh(request.refreshToken()));
    }

    @GetMapping("/me")
    public ApiResponse<MpAuthService.MeView> me(@RequestParam(required = false) String storeId) {
        Long parsed = null;
        if (storeId != null && !storeId.isBlank()) {
            try {
                parsed = Long.parseLong(storeId);
            } catch (NumberFormatException exception) {
                throw BizException.badRequest("门店不存在");
            }
        }
        return ApiResponse.ok(mpAuthService.me(parsed));
    }

    @PostMapping("/files/presign")
    public ApiResponse<PresignView> presign(@Valid @RequestBody PresignRequest request) {
        if (!"FACE".equals(request.biz())) {
            throw BizException.badRequest("暂不支持该上传类型");
        }
        return ApiResponse.ok(new PresignView(mpAuthService.presign(request.contentType())));
    }

    @PostMapping("/files/upload")
    public ApiResponse<Void> upload(@RequestHeader("X-Object-Key") String objectKey, @RequestBody byte[] body) {
        mpAuthService.savePhoto(objectKey, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/face")
    public ApiResponse<MpAuthService.LoginView> face(@Valid @RequestBody FaceRequest request) {
        return ApiResponse.ok(mpAuthService.enroll(request.objectKey()));
    }

    @PostMapping("/face/replace")
    public ApiResponse<Void> replace(@Valid @RequestBody FaceRequest request) {
        mpAuthService.replaceFace(request.objectKey());
        return ApiResponse.ok(null);
    }

    public record CodeRequest(@NotBlank String code) {
    }

    public record PhoneRequest(@NotBlank String phoneCode) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record PresignRequest(@NotBlank String biz, @NotBlank String contentType) {
    }

    public record PresignView(String objectKey) {
    }

    public record FaceRequest(@NotBlank String objectKey) {
    }
}
