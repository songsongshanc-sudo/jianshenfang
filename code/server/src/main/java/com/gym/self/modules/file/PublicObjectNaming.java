package com.gym.self.modules.file;

import com.gym.self.common.api.BizException;

final class PublicObjectNaming {

    private PublicObjectNaming() {
    }

    static void validate(String biz, String contentType, byte[] body) {
        if (body == null || body.length == 0 || body.length > 5 * 1024 * 1024) {
            throw BizException.badRequest("图片大小需在 5MB 以内");
        }
        folder(biz);
        extension(contentType);
    }

    static String folder(String biz) {
        return switch (biz) {
            case "BANNER" -> "banner";
            case "COVER" -> "cover";
            case "GUIDE" -> "guide";
            case "EQUIPMENT" -> "equipment";
            case "RICH" -> "rich";
            case "SERVICE_GUIDE" -> "service-guide";
            case "COACH", "CERT" -> "coach";
            case "PACK" -> "pack";
            default -> throw BizException.badRequest("不支持该图片用途");
        };
    }

    static String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw BizException.badRequest("只支持 jpg、png、webp、gif");
        };
    }
}
