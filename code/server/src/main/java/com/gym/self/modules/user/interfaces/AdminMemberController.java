package com.gym.self.modules.user.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.user.application.AdminMemberService;
import com.gym.self.modules.user.application.GateMemberImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class AdminMemberController {

    private final AdminMemberService adminMemberService;
    private final GateMemberImportService gateMemberImportService;

    public AdminMemberController(AdminMemberService adminMemberService, GateMemberImportService gateMemberImportService) {
        this.adminMemberService = adminMemberService;
        this.gateMemberImportService = gateMemberImportService;
    }

    @GetMapping("/api/admin/members")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String storeId,
                                                       @RequestParam(required = false) String name,
                                                       @RequestParam(required = false) String phone) {
        return ApiResponse.ok(adminMemberService.list(CurrentAdmin.get(), parseOptional(storeId), name, phone));
    }

    @PostMapping("/api/admin/members")
    public ApiResponse<Map<String, String>> create(@Valid @RequestBody CreateMember request) {
        return ApiResponse.ok(adminMemberService.create(CurrentAdmin.get(), parse(request.storeId()), request.name(),
                request.phone(), request.faceBase64(), parseIds(request.cardIds()), parseIds(request.packIds())));
    }

    @PostMapping("/api/admin/members/import")
    public ApiResponse<Map<String, Object>> importMembers(@Valid @RequestBody ImportRequest request) {
        return ApiResponse.ok(gateMemberImportService.importFromGate(CurrentAdmin.get(), parse(request.deviceId())));
    }

    @PostMapping("/api/admin/members/{id}/invite")
    public ApiResponse<Map<String, String>> renewInvite(@PathVariable String id) {
        return ApiResponse.ok(Map.of("inviteCode", adminMemberService.renewInvite(CurrentAdmin.get(), parse(id))));
    }

    @PutMapping("/api/admin/members/{id}/name")
    public ApiResponse<Void> changeName(@PathVariable String id, @Valid @RequestBody NameRequest request) {
        adminMemberService.changeName(CurrentAdmin.get(), parse(id), request.name());
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/admin/members/{id}/phone")
    public ApiResponse<Void> changePhone(@PathVariable String id, @Valid @RequestBody PhoneRequest request) {
        adminMemberService.changePhone(CurrentAdmin.get(), parse(id), request.phone());
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/members/{id}/grants")
    public ApiResponse<Void> grants(@PathVariable String id, @Valid @RequestBody GrantRequest request) {
        adminMemberService.grantMore(CurrentAdmin.get(), parse(id), parse(request.storeId()),
                parseIds(request.cardIds()), parseIds(request.packIds()));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/privacy-url")
    public ApiResponse<Map<String, String>> privacy() {
        return ApiResponse.ok(Map.of("url", adminMemberService.privacyUrl()));
    }

    @PutMapping("/api/admin/privacy-url")
    public ApiResponse<Void> savePrivacy(@RequestBody PrivacyRequest request) {
        adminMemberService.savePrivacyUrl(CurrentAdmin.get(), request.url());
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/mp/privacy")
    public ApiResponse<Map<String, String>> mpPrivacy() {
        return ApiResponse.ok(Map.of("url", adminMemberService.privacyUrl()));
    }

    private static List<Long> parseIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(id -> id != null && !id.isBlank()).map(AdminMemberController::parse).toList();
    }

    private static Long parseOptional(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return parse(id);
    }

    private static long parse(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw BizException.badRequest("参数不正确");
        }
    }

    public record CreateMember(@NotBlank String storeId, @NotBlank String name, @NotBlank String phone,
                               @NotBlank String faceBase64, List<String> cardIds, List<String> packIds) {
    }

    public record ImportRequest(@NotBlank String deviceId) {
    }

    public record GrantRequest(@NotBlank String storeId, List<String> cardIds, List<String> packIds) {
    }

    public record PhoneRequest(@NotBlank String phone) {
    }

    public record NameRequest(@NotBlank String name) {
    }

    public record PrivacyRequest(String url) {
    }
}
