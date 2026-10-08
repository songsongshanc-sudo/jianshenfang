package com.gym.self.modules.user.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class MpJwtFilter extends OncePerRequestFilter {

    private final MpJwt mpJwt;
    private final MpTokenStore tokenStore;

    public MpJwtFilter(MpJwt mpJwt, MpTokenStore tokenStore) {
        this.mpJwt = mpJwt;
        this.tokenStore = tokenStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/mp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var claims = mpJwt.parse(header.substring(7));
                String kind = claims.get("kind", String.class);
                if (tokenStore.valid(claims.getId()) && ("access".equals(kind) || "face".equals(kind) || "wx_session".equals(kind))) {
                    MpPrincipal principal = new MpPrincipal(Long.parseLong(claims.getSubject()), kind);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
