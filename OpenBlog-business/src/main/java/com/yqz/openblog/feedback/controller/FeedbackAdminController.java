package com.yqz.openblog.feedback.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.feedback.dto.FeedbackListItemResponse;
import com.yqz.openblog.feedback.service.FeedbackService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/feedback")
@CrossOrigin(origins = "*")
public class FeedbackAdminController {

    private final FeedbackService feedbackService;

    public FeedbackAdminController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<FeedbackListItemResponse>> listPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.ok(feedbackService.listPending(page, size));
    }
}

