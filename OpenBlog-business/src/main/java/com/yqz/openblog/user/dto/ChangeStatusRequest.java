package com.yqz.openblog.user.dto;

import jakarta.validation.constraints.NotNull;

public class ChangeStatusRequest {
    @NotNull
    private String status; // ACTIVE / BANNED / RESTRICTED

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

