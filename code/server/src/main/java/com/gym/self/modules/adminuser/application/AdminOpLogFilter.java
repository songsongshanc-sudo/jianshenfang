package com.gym.self.modules.adminuser.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.modules.adminuser.auth.AdminPrincipal;
import com.gym.self.modules.adminuser.auth.CurrentAdmin;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AdminOpLogFilter extends OncePerRequestFilter {

    private static final Set<String> HIDDEN = Set.of(
            "password", "faceBase64", "wifiPassword", "secret", "token", "privateKey", "image");
    private static final int MAX_BODY = 8192;

    private final AdminOpLog adminOpLog;
    private final ObjectMapper objectMapper;

    public AdminOpLogFilter(AdminOpLog adminOpLog, ObjectMapper objectMapper) {
        this.adminOpLog = adminOpLog;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (!uri.startsWith("/api/admin/") || uri.startsWith("/api/admin/auth/login")) {
            return true;
        }
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        return "POST".equals(method) && "/api/admin/members".equals(uri)
                || "POST".equals(method) && uri.matches("/api/admin/members/\\d+/grants")
                || "POST".equals(method) && uri.matches("/api/admin/members/\\d+/edits")
                || "POST".equals(method) && uri.matches("/api/admin/members/\\d+/unbind")
                || "PUT".equals(method) && uri.matches("/api/admin/members/\\d+/phone")
                || "PUT".equals(method) && uri.matches("/api/admin/members/\\d+/name")
                || "POST".equals(method) && "/api/admin/members/import".equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        filterChain.doFilter(wrapped, response);
        if (response.getStatus() >= 400) {
            return;
        }
        try {
            AdminPrincipal actor = CurrentAdmin.get();
            String uri = request.getRequestURI();
            String detail = request.getMethod() + " " + uri;
            String fields = fields(wrapped);
            if (!fields.isEmpty()) {
                detail = detail + "。" + fields;
            }
            adminOpLog.record(actor, storeId(actor, uri, wrapped), null, pathId(uri), title(request.getMethod(), uri), detail);
        } catch (RuntimeException ignored) {
            // 未登录或写日志失败不影响原操作
        }
    }

    private String title(String method, String uri) {
        if ("POST".equals(method) && "/api/admin/stores".equals(uri)) return "新增门店";
        if ("PUT".equals(method) && uri.matches("/api/admin/stores/\\d+")) return "更新门店";
        if ("POST".equals(method) && "/api/admin/cards".equals(uri)) return "新增会员卡";
        if ("PUT".equals(method) && uri.matches("/api/admin/cards/\\d+")) return "更新会员卡";
        if ("POST".equals(method) && "/api/admin/coaches".equals(uri)) return "新增教练";
        if ("PUT".equals(method) && uri.matches("/api/admin/coaches/\\d+")) return "更新教练";
        if ("DELETE".equals(method) && uri.matches("/api/admin/coaches/\\d+")) return "删除教练";
        if ("POST".equals(method) && "/api/admin/packs".equals(uri)) return "新增课程";
        if ("PUT".equals(method) && uri.matches("/api/admin/packs/\\d+")) return "更新课程";
        if ("DELETE".equals(method) && uri.matches("/api/admin/packs/\\d+")) return "删除课程";
        if ("POST".equals(method) && "/api/admin/gates".equals(uri)) return "新增闸机";
        if ("PUT".equals(method) && uri.matches("/api/admin/gates/\\d+")) return "更新闸机";
        if ("DELETE".equals(method) && uri.matches("/api/admin/gates/\\d+")) return "删除闸机";
        if ("POST".equals(method) && "/api/admin/accounts".equals(uri)) return "新增账号";
        if ("DELETE".equals(method) && uri.matches("/api/admin/accounts/\\d+")) return "删除账号";
        if ("POST".equals(method) && uri.matches("/api/admin/accounts/\\d+/disable")) return "停用账号";
        if ("POST".equals(method) && uri.matches("/api/admin/accounts/\\d+/password")) return "重置账号密码";
        if ("DELETE".equals(method)) return "删除";
        if ("POST".equals(method)) return "新增";
        if ("PUT".equals(method)) return "更新";
        return "系统操作";
    }

    private Long storeId(AdminPrincipal actor, String uri, ContentCachingRequestWrapper request) {
        if (actor.storeId() != null) {
            return actor.storeId();
        }
        Matcher store = Pattern.compile("/api/admin/stores/(\\d+)").matcher(uri);
        if (store.find()) {
            return Long.parseLong(store.group(1));
        }
        JsonNode node = body(request);
        if (node != null && node.hasNonNull("storeId")) {
            try {
                return Long.parseLong(node.get("storeId").asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long pathId(String uri) {
        Matcher matcher = Pattern.compile("/(\\d+)(?:/|$)").matcher(uri);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }

    private String fields(ContentCachingRequestWrapper request) {
        JsonNode node = body(request);
        if (node == null || !node.isObject()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (HIDDEN.contains(name) || node.get(name).isContainerNode()) {
                continue;
            }
            String value = node.get(name).asText("");
            if (value.isBlank() || value.length() > 80) {
                continue;
            }
            if (!text.isEmpty()) {
                text.append("，");
            }
            text.append(name).append(" ").append(value);
        }
        return text.toString();
    }

    private JsonNode body(ContentCachingRequestWrapper request) {
        byte[] raw = request.getContentAsByteArray();
        if (raw.length == 0 || raw.length > MAX_BODY) {
            return null;
        }
        try {
            return objectMapper.readTree(new String(raw, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return null;
        }
    }
}
