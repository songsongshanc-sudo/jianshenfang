package com.gym.self.modules.file;

import com.gym.self.common.api.BizException;

final class PublicObjectNaming {

    private PublicObjectNaming() {
    }

    static void validate(String biz, String contentType, byte[] body) {
        boolean video = "EQUIPMENT_VIDEO".equals(biz);
        int limit = video ? 200 * 1024 * 1024 : 5 * 1024 * 1024;
        if (body == null || body.length == 0 || body.length > limit) {
            throw BizException.badRequest(video ? "视频大小需在 200MB 以内" : "图片大小需在 5MB 以内");
        }
        folder(biz);
        extension(biz, contentType);
    }

    static String folder(String biz) {
        return switch (biz) {
            case "BANNER" -> "banner";
            case "COVER" -> "cover";
            case "GUIDE" -> "guide";
            case "EQUIPMENT", "EQUIPMENT_VIDEO" -> "equipment";
            case "RICH" -> "rich";
            case "COACH", "CERT" -> "coach";
            case "PACK" -> "pack";
            default -> throw BizException.badRequest("不支持该图片用途");
        };
    }

    static String extension(String biz, String contentType) {
        if ("EQUIPMENT_VIDEO".equals(biz)) {
            return switch (contentType) {
                case "video/mp4" -> ".mp4";
                case "video/webm" -> ".webm";
                case "video/quicktime" -> ".mov";
                default -> throw BizException.badRequest("只支持 mp4、webm、mov");
            };
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw BizException.badRequest("只支持 jpg、png、webp、gif");
        };
    }
}
