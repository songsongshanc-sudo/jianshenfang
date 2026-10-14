package com.gym.self.modules.visit.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.visit.application.VisitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/visit")
public class AdminVisitController {

    private final VisitService visitService;

    public AdminVisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @GetMapping("/config")
    public ApiResponse<VisitService.ConfigView> config() {
        return ApiResponse.ok(visitService.adminGet(CurrentAdmin.get()));
    }

    @PutMapping("/config")
    public ApiResponse<Void> save(@Valid @RequestBody SaveRequest request) {
        visitService.adminSave(CurrentAdmin.get(), new VisitService.ConfigUpdate(
                Boolean.TRUE.equals(request.enabled()), request.depositFen(), request.durationMinutes(),
                request.validDays(), request.warnMinutes(), request.notice()));
        return ApiResponse.ok(null);
    }

    @GetMapping("/passes")
    public ApiResponse<List<VisitService.AdminVisit>> list(@RequestParam(required = false) String storeId) {
        Long scoped = storeId == null || storeId.isBlank() ? null : Long.parseLong(storeId);
        return ApiResponse.ok(visitService.adminList(CurrentAdmin.get(), scoped));
    }

    public record SaveRequest(
            @NotNull Boolean enabled,
            @NotNull Long depositFen,
            @NotNull Integer durationMinutes,
            @NotNull Integer validDays,
            @NotNull Integer warnMinutes,
            @Size(max = 2000) String notice) {
    }
}
