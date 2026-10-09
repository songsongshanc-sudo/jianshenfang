package com.gym.self.modules.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.adminuser.auth.StoreScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GateController {

    private final GateService gateService;
    private final ObjectMapper objectMapper;

    public GateController(GateService gateService, ObjectMapper objectMapper) {
        this.gateService = gateService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/admin/gates")
    public ApiResponse<IdView> create(@Valid @RequestBody CreateDevice request) {
        return ApiResponse.ok(new IdView(gateService.createDevice(CurrentAdmin.get(), parseId(request.storeId()), request.name(), request.deviceSn())));
    }

    @PostMapping("/api/admin/gates/{id}/token")
    public ApiResponse<TokenView> token(@PathVariable String id) {
        return ApiResponse.ok(new TokenView(gateService.rotateToken(CurrentAdmin.get(), parseId(id))));
    }

    @PostMapping("/api/admin/gates/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable String id) {
        gateService.retryFailed(CurrentAdmin.get(), parseId(id));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/gates/{id}/secret")
    public ApiResponse<SecretView> secret(@PathVariable String id) {
        return ApiResponse.ok(new SecretView(gateService.resetSecret(CurrentAdmin.get(), parseId(id))));
    }

    @PostMapping("/api/admin/gates/{id}/status")
    public ApiResponse<Void> status(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        gateService.setStatus(CurrentAdmin.get(), parseId(id), request.status());
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/gates")
    public ApiResponse<List<GateService.DeviceView>> devices(@RequestParam(required = false) String storeId) {
        return ApiResponse.ok(gateService.devices(CurrentAdmin.get(), parseOptional(storeId)));
    }

    @GetMapping("/api/admin/doors")
    public ApiResponse<List<GateService.DoorView>> doors(@RequestParam(required = false) String storeId) {
        return ApiResponse.ok(gateService.doors(CurrentAdmin.get(), parseOptional(storeId)));
    }

    @GetMapping("/api/admin/online")
    public ApiResponse<Integer> online(@RequestParam(required = false) String storeId) {
        var actor = CurrentAdmin.get();
        Long scoped = StoreScope.requiredStore(actor, parseOptional(storeId));
        if (scoped != null) {
            return ApiResponse.ok(gateService.onlineCount(scoped));
        }
        int sum = gateService.devices(actor, null).stream()
                .map(GateService.DeviceView::storeId)
                .distinct()
                .mapToInt(id -> gateService.onlineCount(Long.parseLong(id)))
                .sum();
        return ApiResponse.ok(sum);
    }

    @PostMapping("/api/gate/v1/heartbeat")
    public ApiResponse<Void> heartbeat(
            @RequestHeader(value = "X-Device-Sn", required = false) String sn,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody(required = false) String body) {
        gateService.heartbeat(sn, timestamp, nonce, signature, body == null ? "" : body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/gate/v1/verify")
    public ApiResponse<GateService.VerifyResult> verify(
            @RequestHeader(value = "X-Device-Sn", required = false) String sn,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody(required = false) String body) throws Exception {
        String raw = body == null ? "" : body;
        JsonNode node = raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        String memberNo = node.path("memberNo").asText("");
        String vendorFaceId = node.path("vendorFaceId").asText("");
        return ApiResponse.ok(gateService.verify(sn, timestamp, nonce, signature, raw, memberNo, vendorFaceId));
    }

    private static Long parseOptional(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return parseId(id);
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("参数不正确");
        }
    }

    public record CreateDevice(@NotBlank String storeId, @NotBlank String name, String deviceSn) {
    }

    public record TokenView(String token) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record IdView(String id) {
    }

    public record SecretView(String secret) {
    }
}
