package com.yqz.openblog.feedback.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.feedback.dto.FeedbackCreateRequest;
import com.yqz.openblog.feedback.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
@CrossOrigin(origins = "*")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody @Valid FeedbackCreateRequest req, HttpServletRequest request) {
        feedbackService.create(req, request);
        return ApiResponse.ok();
    }
}

