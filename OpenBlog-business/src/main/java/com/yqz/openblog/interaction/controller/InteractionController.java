package com.yqz.openblog.interaction.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.interaction.service.InteractionService;
import com.yqz.openblog.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class InteractionController {

    private final InteractionService interactionService;
    private final CurrentUser currentUser;

    public InteractionController(InteractionService interactionService, CurrentUser currentUser) {
        this.interactionService = interactionService;
        this.currentUser = currentUser;
    }

    @PostMapping("/articles/{articleId}/likes")
    public ApiResponse<Void> like(@PathVariable("articleId") Long articleId) {
        interactionService.like(articleId, currentUser.userId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/articles/{articleId}/likes")
    public ApiResponse<Void> unlike(@PathVariable("articleId") Long articleId) {
        interactionService.unlike(articleId, currentUser.userId());
        return ApiResponse.ok();
    }

    @PostMapping("/articles/{articleId}/favorites")
    public ApiResponse<Void> favorite(@PathVariable("articleId") Long articleId) {
        interactionService.favorite(articleId, currentUser.userId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/articles/{articleId}/favorites")
    public ApiResponse<Void> unfavorite(@PathVariable("articleId") Long articleId) {
        interactionService.unfavorite(articleId, currentUser.userId());
        return ApiResponse.ok();
    }

    @PostMapping("/follows/{userId}")
    public ApiResponse<Void> follow(@PathVariable("userId") Long userId) {
        interactionService.follow(userId, currentUser.userId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/follows/{userId}")
    public ApiResponse<Void> unfollow(@PathVariable("userId") Long userId) {
        interactionService.unfollow(userId, currentUser.userId());
        return ApiResponse.ok();
    }
}

