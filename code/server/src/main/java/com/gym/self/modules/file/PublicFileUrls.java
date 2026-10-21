package com.gym.self.modules.file;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;

/**
 * 本机调试仍返回 127.0.0.1。从公网打开管理端时，用当前网址，避免把图片指回上传者自己的电脑。
 * 公网路径放在 /api/uploads/ 下，现有 Nginx 的 /api/ 就能转到后端。
 */
@Component
public class PublicFileUrls {

    private final String configured;

    public PublicFileUrls(@Value("${gym.files.public-base-url}") String publicBaseUrl) {
        this.configured = trimSlash(publicBaseUrl);
    }

    public String urlFor(String folder, String name) {
        String requestBase = requestBase();
        if (publicBase(requestBase)) {
            return requestBase + "/api/uploads/" + folder + "/" + name;
        }
        if (!isLocal(configured)) {
            return configured + "/api/uploads/" + folder + "/" + name;
        }
        return configured + "/uploads/" + folder + "/" + name;
    }

    public String rewriteJson(String json) {
        String requestBase = requestBase();
        String prefix;
        if (publicBase(requestBase)) {
            prefix = requestBase + "/api/uploads/";
        } else if (!isLocal(configured)) {
            prefix = configured + "/api/uploads/";
        } else {
            return json;
        }
        return json
                .replace("http://127.0.0.1:8080/uploads/", prefix)
                .replace("http://localhost:8080/uploads/", prefix)
                .replace("https://127.0.0.1:8080/uploads/", prefix)
                .replace("https://localhost:8080/uploads/", prefix);
    }

    private boolean publicBase(String base) {
        return base != null && !base.isBlank() && !isLocal(base);
    }

    private static boolean isLocal(String base) {
        if (base == null || base.isBlank()) {
            return true;
        }
        try {
            String host = URI.create(base).getHost();
            return host == null || "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host);
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private static String requestBase() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String proto = first(request.getHeader("X-Forwarded-Proto"));
        if (proto == null || proto.isBlank()) {
            proto = request.getScheme();
        }
        String host = first(request.getHeader("X-Forwarded-Host"));
        if (host == null || host.isBlank()) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            return null;
        }
        return proto + "://" + host;
    }

    private static String first(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int comma = value.indexOf(',');
        return (comma < 0 ? value : value.substring(0, comma)).trim();
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
