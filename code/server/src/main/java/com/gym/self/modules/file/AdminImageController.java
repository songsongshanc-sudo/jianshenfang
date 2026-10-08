package com.gym.self.modules.file;

import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.BizException;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import com.gym.self.modules.adminuser.auth.StoreScope;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/files")
public class AdminImageController {

    private final PublicImageStorage publicImageStorage;

    public AdminImageController(PublicImageStorage publicImageStorage) {
        this.publicImageStorage = publicImageStorage;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageView> upload(@RequestParam String biz, @RequestParam("file") MultipartFile file) throws IOException {
        if (!"GUIDE".equals(biz)) {
            StoreScope.requireMaster(CurrentAdmin.get());
        }
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择图片");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType();
        String url = publicImageStorage.store(biz, contentType, file.getBytes());
        return ApiResponse.ok(new ImageView(url));
    }

    public record ImageView(String url) {
    }
}
