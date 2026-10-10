package com.gym.self.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.self.common.api.ApiResponse;
import com.gym.self.common.api.ErrorCode;
import com.gym.self.modules.adminuser.auth.AdminJwtFilter;
import com.gym.self.modules.user.auth.MpJwtFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper, AdminJwtFilter jwtFilter,
                                            MpJwtFilter mpJwtFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/v3/api-docs/**", "/doc.html", "/webjars/**").permitAll()
                        .requestMatchers("/api/admin/auth/login").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
                        write(response, objectMapper, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "未登录")))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(mpJwtFilter, AdminJwtFilter.class);
        return http.build();
    }

    /** 允许本地 H5 / 管理端跨域预览（微信小程序本身不受 CORS 限制）。 */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    static void write(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, int code, String message)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }
}
