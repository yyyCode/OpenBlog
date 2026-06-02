package com.yqz.openblog.user.dto;

import jakarta.validation.constraints.NotBlank;

public class ChangeRoleRequest {

    @NotBlank
    private String role; // ADMIN, AUTHOR, READER

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
