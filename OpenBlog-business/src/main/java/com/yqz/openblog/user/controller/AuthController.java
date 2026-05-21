package com.yqz.openblog.user.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.user.dto.AuthResponse;
import com.yqz.openblog.user.dto.ChangePasswordRequest;
import com.yqz.openblog.user.dto.LoginRequest;
import com.yqz.openblog.user.dto.SliderChallengeResponse;
import com.yqz.openblog.user.dto.SliderCompleteRequest;
import com.yqz.openblog.user.dto.MeResponse;
import com.yqz.openblog.user.dto.RefreshRequest;
import com.yqz.openblog.user.dto.RegisterRequest;
import com.yqz.openblog.user.dto.UserUpdateRequest;
import com.yqz.openblog.user.service.AuthService;
import com.yqz.openblog.user.service.SliderVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final SliderVerificationService sliderVerificationService;

    public AuthController(AuthService authService, SliderVerificationService sliderVerificationService) {
        this.authService = authService;
        this.sliderVerificationService = sliderVerificationService;
    }

    @GetMapping("/auth/slider-challenge")
    public ApiResponse<SliderChallengeResponse> sliderChallenge(HttpServletRequest request) {
        return ApiResponse.ok(sliderVerificationService.issue(request));
    }

    @PostMapping("/auth/slider-complete")
    public ApiResponse<Void> sliderComplete(
            @RequestBody @Valid SliderCompleteRequest req,
            HttpServletRequest request) {
        sliderVerificationService.complete(request, req.getChallengeId());
        return ApiResponse.ok();
    }

    @PostMapping("/auth/register")
    public ApiResponse<Void> register(@RequestBody @Valid RegisterRequest req) {
        authService.register(req);
        return ApiResponse.ok();
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

