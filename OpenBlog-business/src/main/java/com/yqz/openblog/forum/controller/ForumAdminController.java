package com.yqz.openblog.forum.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.forum.dto.ForumTopicResponse;
import com.yqz.openblog.forum.service.ForumService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/forum")
@CrossOrigin(origins = "*")
public class ForumAdminController {

    private final ForumService forumService;

    public ForumAdminController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/topics")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<PageResult<ForumTopicResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(forumService.listAllTopics(page, size, status));
    }

    @PutMapping("/topics/{topicId}/hide")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<Void> hide(@PathVariable Long topicId) {
        forumService.hideTopic(topicId);
        return ApiResponse.ok();
    }

    @PutMapping("/topics/{topicId}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<Void> publish(@PathVariable Long topicId) {
        forumService.publishTopic(topicId);
        return ApiResponse.ok();
    }
}
