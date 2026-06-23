package com.shm.admin.config;

import com.shm.admin.mapper.UserMapper;
import com.shm.admin.security.CurrentUserArgumentResolver;
import com.shm.admin.security.JwtAuthFilter;
import com.shm.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Admin Service 安全配置
 *
 * <p>仅 admin / cs 角色可访问所有接口。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public SecurityConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    /**
     * JWT 工具 Bean — 从 application.yml 读取密钥
     */
    @Bean
    public static JwtUtil jwtUtil(
            @Value("${jwt.access-secret}") String accessSecret,
            @Value("${jwt.refresh-secret}") String refreshSecret) {
        return new JwtUtil(accessSecret, refreshSecret);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil, UserMapper userMapper,
                                           @Value("${admin.auto-phones:}") String adminAutoPhones) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        // Swagger UI + API docs (Phase 12)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtUtil, userMapper, adminAutoPhones),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
