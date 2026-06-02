package com.yqz.openblog.security;

import com.yqz.openblog.config.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtService jwtService,
                           RestAuthenticationEntryPoint authenticationEntryPoint,
                           RestAccessDeniedHandler accessDeniedHandler,
                           CorsProperties corsProperties) {
        this.jwtService = jwtService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // 允许跨域（前端会有 OPTIONS 预检请求；否则可能被安全策略拦截）
                .cors(Customizer.withDefaults())
                .headers(h -> h.referrerPolicy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // SEO pages
                        .requestMatchers(HttpMethod.GET, "/seo/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/sitemap.xml").permitAll()
                        .requestMatchers(HttpMethod.GET, "/robots.txt").permitAll()
                        // Auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // 放行 CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/api/v1/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public home
                        .requestMatchers(HttpMethod.GET, "/api/v1/home").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/site/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/site/visit").permitAll()
                        // Public profile (for avatar/signature)
                        .requestMatchers(HttpMethod.GET, "/api/v1/profile").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/changelog").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/changelog/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/feedback").permitAll()
                        // Public articles/comments
                        .requestMatchers(HttpMethod.GET, "/api/v1/articles/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/**").permitAll()
                        // Other write operations require auth
                        .anyRequest().authenticated()
                )
                // 本项目只用 JWT，不需要 Basic 认证挑战（否则会干扰 OPTIONS 预检）
                .httpBasic(AbstractHttpConfigurer::disable);

        http.addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        // 强制忽略 OPTIONS 预检：避免进入认证链导致 401/Basic challenge
        return (web) -> web.ignoring().requestMatchers(new AntPathRequestMatcher("/**", "OPTIONS"));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> patterns = corsProperties.getAllowedOriginPatterns();
        if (patterns == null || patterns.isEmpty()) {
            patterns = List.of("*");
        }
        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
        // 显式注册 CorsFilter，确保浏览器预检 OPTIONS 能正确返回 CORS 头
        return new CorsFilter(corsConfigurationSource);
    }
}

