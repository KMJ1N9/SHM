package com.shm.core.config;

import com.shm.common.util.JwtUtil;
import com.shm.core.repository.UserRepository;
import com.shm.core.security.CurrentUserArgumentResolver;
import com.shm.core.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * Spring Security 配置（与 Node.js middleware/auth.js 行为一致）
 *
 * <p>关键决策：
 * <ul>
 *   <li>禁用 CSRF（小程序无 Cookie，无 CSRF 风险）</li>
 *   <li>无状态 Session（JWT 自带状态）</li>
 *   <li>JwtAuthFilter 插入 UsernamePasswordAuthenticationFilter 之前</li>
 *   <li>白名单放行 login/refresh/health，其余全部需要 JWT</li>
 * </ul>
 *
 * <p>{@code @ConfigurationProperties} 注册由 {@code CoreApplication} 上的
 * {@code @ConfigurationPropertiesScan} 统一处理，此处不再用 {@code @EnableConfigurationProperties}。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig implements WebMvcConfigurer {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepo;
    private final StringRedisTemplate redis;
    private final CurrentUserArgumentResolver currentUserResolver;

    public SecurityConfig(JwtUtil jwtUtil, UserRepository userRepo,
                          StringRedisTemplate redis,
                          CurrentUserArgumentResolver currentUserResolver) {
        this.jwtUtil = jwtUtil;
        this.userRepo = userRepo;
        this.redis = redis;
        this.currentUserResolver = currentUserResolver;
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（小程序无 Cookie）
            .csrf(csrf -> csrf.disable())
            // 无状态 Session（JWT 自带状态）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 路由权限
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("POST", "/api/auth/login").permitAll()
                .requestMatchers("POST", "/api/auth/refresh").permitAll()
                .requestMatchers("GET", "/api/health").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/internal/**").permitAll()
                // Swagger UI + API docs (Phase 12)
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Actuator endpoints (Prometheus metrics, health)
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            // 添加 JWT Filter
            .addFilterBefore(
                new JwtAuthFilter(jwtUtil, userRepo, redis),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    /**
     * 注册 @CurrentUser 参数解析器
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
