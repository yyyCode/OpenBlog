package com.yqz.openblog.forum.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.BizException;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.forum.dto.ForumCommentRequest;
import com.yqz.openblog.forum.dto.ForumCommentResponse;
import com.yqz.openblog.forum.dto.ForumTopicCreateRequest;
import com.yqz.openblog.forum.dto.ForumTopicResponse;
import com.yqz.openblog.forum.service.ForumService;
import com.yqz.openblog.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/forum")
@CrossOrigin(origins = "*")
public class ForumController {

    private final ForumService forumService;
    private final CurrentUser currentUser;

    public ForumController(ForumService forumService, CurrentUser currentUser) {
        this.forumService = forumService;
        this.currentUser = currentUser;
    }

    @GetMapping("/topics")
    public ApiResponse<PageResult<ForumTopicResponse>> listTopics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(forumService.listTopics(page, size));
    }

    @GetMapping("/topics/{topicId}")
    public ApiResponse<ForumTopicResponse> getTopic(@PathVariable Long topicId) {
        return ApiResponse.ok(forumService.getTopic(topicId));
    }

    @PostMapping("/topics")
    public ApiResponse<ForumTopicResponse> createTopic(@Valid @RequestBody ForumTopicCreateRequest req) {
        Long uid = currentUser.userId();
        if (uid == null) {
            throw new BizException(4011, "请先登录");
        }
        return ApiResponse.ok(forumService.createTopic(uid, req));
    }

    @GetMapping("/topics/{topicId}/comments")
    public ApiResponse<PageResult<ForumCommentResponse>> listComments(
            @PathVariable Long topicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(forumService.listComments(topicId, page, size));
    }

    @PostMapping("/topics/{topicId}/comments")
    public ApiResponse<ForumCommentResponse> createComment(
            @PathVariable Long topicId,
            @Valid @RequestBody ForumCommentRequest req) {
        Long uid = currentUser.userId();
        if (uid == null) {
            throw new BizException(4011, "请先登录");
        }
        return ApiResponse.ok(forumService.createComment(topicId, uid, req));
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId) {
        Long uid = currentUser.userId();
        if (uid == null) {
            throw new BizException(4011, "请先登录");
        }
        forumService.deleteComment(commentId, uid, currentUser.isAdmin());
        return ApiResponse.ok();
    }
}
