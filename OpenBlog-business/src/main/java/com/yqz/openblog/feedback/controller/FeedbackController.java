package com.yqz.openblog.feedback.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.feedback.dto.FeedbackCreateRequest;
import com.yqz.openblog.feedback.service.FeedbackService;
import com.yqz.openblog.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
@CrossOrigin(origins = "*")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final CurrentUser currentUser;

    public FeedbackController(FeedbackService feedbackService, CurrentUser currentUser) {
        this.feedbackService = feedbackService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ApiResponse<Void> create(@RequestBody @Valid FeedbackCreateRequest req, HttpServletRequest request) {
        Long uid = currentUser.userId();
        if (uid == null) {
            throw new BizException(4011, "请先登录");
        }
        feedbackService.create(req, uid, request);
        return ApiResponse.ok();
    }
}
