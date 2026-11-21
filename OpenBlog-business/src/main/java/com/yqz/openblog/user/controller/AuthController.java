package com.yqz.openblog.user.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.user.dto.AuthResponse;
import com.yqz.openblog.user.dto.ChangePasswordRequest;
import com.yqz.openblog.user.dto.EmailCodeRequest;
import com.yqz.openblog.user.dto.EmailCodeResponse;
import com.yqz.openblog.user.dto.LoginRequest;
import com.yqz.openblog.user.dto.MeResponse;
import com.yqz.openblog.user.dto.RefreshRequest;
import com.yqz.openblog.user.dto.RegisterRequest;
import com.yqz.openblog.user.dto.UserUpdateRequest;
import com.yqz.openblog.user.service.AuthService;
import com.yqz.openblog.user.service.EmailCodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    public AuthController(AuthService authService, EmailCodeService emailCodeService) {
        this.authService = authService;
        this.emailCodeService = emailCodeService;
    }

    @PostMapping("/auth/email-code")
    public ApiResponse<EmailCodeResponse> emailCode(@RequestBody @Valid EmailCodeRequest req) {
        EmailCodeResponse resp = new EmailCodeResponse();
        resp.setCooldownSeconds(emailCodeService.sendCode(req.getEmail(), req.getPurpose()));
        return ApiResponse.ok(resp);
    }

    @PostMapping("/auth/register")
    public ApiResponse<AuthResponse> register(@RequestBody @Valid RegisterRequest req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthResponse> login(@RequestBody @Valid LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/auth/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody @Valid RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req));
    }

    @PostMapping("/auth/change-password")
    public ApiResponse<Void> changePassword(@RequestBody @Valid ChangePasswordRequest req) {
        authService.changePassword(req);
        return ApiResponse.ok();
    }

    @GetMapping("/users/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.me());
    }

    @PutMapping("/users/me")
    public ApiResponse<MeResponse> updateMe(@RequestBody @Valid UserUpdateRequest req) {
        return ApiResponse.ok(authService.updateMe(req));
    }
}

