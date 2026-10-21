package com.gym.self.config;

import com.gym.self.modules.file.PublicFileUrls;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublicUploadUrlFilter extends OncePerRequestFilter {

    private final PublicFileUrls publicFileUrls;

    public PublicUploadUrlFilter(PublicFileUrls publicFileUrls) {
        this.publicFileUrls = publicFileUrls;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith("/api/uploads/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapper);
        byte[] body = wrapper.getContentAsByteArray();
        String type = wrapper.getContentType();
        if (body.length > 0 && type != null && type.contains("json")) {
            String text = new String(body, StandardCharsets.UTF_8);
            String rewritten = publicFileUrls.rewriteJson(text);
            if (!rewritten.equals(text)) {
                byte[] out = rewritten.getBytes(StandardCharsets.UTF_8);
                response.resetBuffer();
                response.setContentLength(out.length);
                response.getOutputStream().write(out);
                return;
            }
        }
        wrapper.copyBodyToResponse();
    }
}
