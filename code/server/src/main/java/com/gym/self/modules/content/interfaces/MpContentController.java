package com.gym.self.modules.content.interfaces;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.modules.content.application.ContentAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mp")
public class MpContentController {

    private final ContentAdminService contentAdminService;

    public MpContentController(ContentAdminService contentAdminService) {
        this.contentAdminService = contentAdminService;
    }

    @GetMapping("/banners")
    public ApiResponse<List<ContentAdminService.BannerView>> banners() {
        return ApiResponse.ok(contentAdminService.banners(true));
    }

    @GetMapping("/notices")
    public ApiResponse<List<ContentAdminService.NoticeView>> notices() {
        return ApiResponse.ok(contentAdminService.notices(true));
    }

    @GetMapping("/agreements/current")
    public ApiResponse<ContentAdminService.AgreementView> currentAgreement() {
        return ApiResponse.ok(contentAdminService.currentAgreement());
    }
}
