package com.pharmaprice.common.config;

import com.pharmaprice.auth.security.JwtAccessDeniedHandler;
import com.pharmaprice.auth.security.JwtAuthenticationEntryPoint;
import com.pharmaprice.auth.security.JwtAuthenticationFilter;
import com.pharmaprice.auth.security.JwtTokenProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * JWT 기반 stateless 인증·인가 설정. {@code docs/ROADMAP.md} T-23.
 *
 * <p>CORS는 이 클래스의 {@link #corsConfigurationSource()} 로 일원화한다
 * (기존 {@code CorsConfig}의 {@code WebMvcConfigurer} 방식은 제거 — 두 메커니즘을
 * 같이 쓰면 서로 다른 설정으로 갈릴 위험이 있다).</p>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // signup/login/refresh만 permitAll — logout/me는 docs/API.md §2가 🔐(인증 필요)로
                // 명시하므로 "/api/v1/auth/**" 전체를 열어두면 안 된다(T-24에서 실측 확인한 버그: 이걸
                // 전체 permitAll로 두면 /auth/me가 401 대신 principal=null로 컨트롤러까지 들어가 500이 난다).
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/drugs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/pharmacies/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/search").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/regions").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
