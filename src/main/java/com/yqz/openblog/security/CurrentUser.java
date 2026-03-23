package com.yqz.openblog.security;

import com.yqz.openblog.user.entity.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public Long userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthUser au) {
            return au.getUserId();
        }
        return null;
    }

    public UserRole role() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthUser au) {
            return au.getRole();
        }
        return null;
    }

    public boolean isAdmin() {
        return UserRole.ADMIN.equals(role());
    }
}

