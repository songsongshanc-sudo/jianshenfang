package com.gym.self.modules.adminuser.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class AdminJwtFilter extends OncePerRequestFilter {

    private final AdminJwt adminJwt;
    private final TokenStore tokenStore;

    public AdminJwtFilter(AdminJwt adminJwt, TokenStore tokenStore) {
        this.adminJwt = adminJwt;
        this.tokenStore = tokenStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/mp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var claims = adminJwt.parse(header.substring(7));
                if ("access".equals(claims.get("kind")) && tokenStore.valid(claims.getId())) {
                    Long storeId = claims.get("storeId", Long.class);
                    AdminPrincipal principal = new AdminPrincipal(
                            Long.parseLong(claims.getSubject()),
                            claims.get("role", String.class),
                            storeId);
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
