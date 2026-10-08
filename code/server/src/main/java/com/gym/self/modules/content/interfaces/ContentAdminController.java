package com.gym.self.modules.content.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.content.application.ContentAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class ContentAdminController {

    private final ContentAdminService contentAdminService;

    public ContentAdminController(ContentAdminService contentAdminService) {
        this.contentAdminService = contentAdminService;
    }

    @GetMapping("/banners")
    public ApiResponse<List<ContentAdminService.BannerView>> banners() {
        return ApiResponse.ok(contentAdminService.adminBanners(CurrentAdmin.get()));
    }

    @PostMapping("/banners")
    public ApiResponse<IdView> createBanner(@Valid @RequestBody BannerRequest request) {
        String id = contentAdminService.createBanner(CurrentAdmin.get(), request.title(), request.imageUrl(),
                request.linkUrl(), request.sortNo(), request.status());
        return ApiResponse.ok(new IdView(id));
    }

    @PutMapping("/banners/{id}")
    public ApiResponse<Void> updateBanner(@PathVariable String id, @Valid @RequestBody BannerRequest request) {
        contentAdminService.updateBanner(CurrentAdmin.get(), parseId(id), request.title(), request.imageUrl(),
                request.linkUrl(), request.sortNo(), request.status());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/banners/{id}")
    public ApiResponse<Void> deleteBanner(@PathVariable String id) {
        contentAdminService.deleteBanner(CurrentAdmin.get(), parseId(id));
        return ApiResponse.ok(null);
    }

    @GetMapping("/notices")
    public ApiResponse<List<ContentAdminService.NoticeView>> notices() {
        return ApiResponse.ok(contentAdminService.adminNotices(CurrentAdmin.get()));
    }

    @PostMapping("/notices")
    public ApiResponse<IdView> createNotice(@Valid @RequestBody NoticeRequest request) {
        String id = contentAdminService.createNotice(CurrentAdmin.get(), request.content(), request.sortNo(), request.status());
        return ApiResponse.ok(new IdView(id));
    }

    @PutMapping("/notices/{id}")
    public ApiResponse<Void> updateNotice(@PathVariable String id, @Valid @RequestBody NoticeRequest request) {
        contentAdminService.updateNotice(CurrentAdmin.get(), parseId(id), request.content(), request.sortNo(), request.status());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/notices/{id}")
    public ApiResponse<Void> deleteNotice(@PathVariable String id) {
        contentAdminService.deleteNotice(CurrentAdmin.get(), parseId(id));
        return ApiResponse.ok(null);
    }

    @GetMapping("/agreements")
    public ApiResponse<List<ContentAdminService.AgreementView>> agreements() {
        return ApiResponse.ok(contentAdminService.agreements(CurrentAdmin.get()));
    }

    @PostMapping("/agreements")
    public ApiResponse<ContentAdminService.AgreementView> createDraft() {
        return ApiResponse.ok(contentAdminService.createDraft(CurrentAdmin.get()));
    }

    @PutMapping("/agreements/{id}")
    public ApiResponse<ContentAdminService.AgreementView> updateDraft(@PathVariable String id,
                                                                      @Valid @RequestBody AgreementRequest request) {
        return ApiResponse.ok(contentAdminService.updateDraft(CurrentAdmin.get(), parseId(id), request.title(), request.content()));
    }

    @PostMapping("/agreements/{id}/publish")
    public ApiResponse<ContentAdminService.AgreementView> publish(@PathVariable String id) {
        return ApiResponse.ok(contentAdminService.publish(CurrentAdmin.get(), parseId(id)));
    }

    @GetMapping("/configs")
    public ApiResponse<ContentAdminService.ConfigView> config() {
        return ApiResponse.ok(contentAdminService.config(CurrentAdmin.get()));
    }

    @PutMapping("/configs")
    public ApiResponse<ContentAdminService.ConfigView> updateConfig(@Valid @RequestBody ConfigRequest request) {
        return ApiResponse.ok(contentAdminService.updateConfig(CurrentAdmin.get(), request.entryDebounceSeconds(),
                request.onlineWindowMinutes(), request.orderExpireMinutes(), request.refundDailyDeductFen()));
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw BizException.badRequest("参数不正确");
        }
    }

    public record BannerRequest(
            @NotBlank @Size(max = 64) String title,
            @NotBlank @Size(max = 512) String imageUrl,
            @Size(max = 512) String linkUrl,
            Integer sortNo,
            @NotBlank String status) {
    }

    public record NoticeRequest(
            @NotBlank @Size(max = 255) String content,
            Integer sortNo,
            @NotBlank String status) {
    }

    public record AgreementRequest(@NotBlank @Size(max = 64) String title, @NotNull String content) {
    }

    public record ConfigRequest(@NotNull Integer entryDebounceSeconds, @NotNull Integer onlineWindowMinutes,
                                @NotNull Integer orderExpireMinutes, String refundDailyDeductFen) {
    }

    public record IdView(String id) {
    }
}
