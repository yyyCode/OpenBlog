package com.yqz.openblog.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.yqz.openblog.user.entity.UserRole;

import java.io.IOException;

/**
 * 解析 Authorization: Bearer xxx 并写入 SecurityContext。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring("Bearer ".length());
        try {
            Long userId = jwtService.getUserIdFromToken(token);
            String roleStr = jwtService.getRoleFromAccessToken(token);
            if (userId != null && roleStr != null) {
                // 允许覆盖匿名认证：匿名鉴权对象不为 null，但 isAuthenticated=false
                // 如果不覆盖，会导致 permitAll 接口（如 /api/v1/profile）拿不到当前用户。
                UserRole role = UserRole.valueOf(roleStr);
                AuthUser authUser = new AuthUser(userId, role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(authUser, null, authUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException ignored) {
            // token 无效：保持匿名，由 Spring Security 的鉴权规则返回 401/403
        }

        chain.doFilter(request, response);
    }
}

