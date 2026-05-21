package com.yqz.openblog.comment.controller;

import com.yqz.openblog.comment.dto.CommentCreateRequest;
import com.yqz.openblog.comment.dto.CommentThreadResponse;
import com.yqz.openblog.comment.service.CommentService;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class CommentController {

    private final CommentService commentService;
    private final CurrentUser currentUser;

    public CommentController(CommentService commentService, CurrentUser currentUser) {
        this.commentService = commentService;
        this.currentUser = currentUser;
    }

    @GetMapping("/articles/{articleId}/comments")
    public ApiResponse<PageResult<CommentThreadResponse>> list(
            @PathVariable("articleId") Long articleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(commentService.listComments(articleId, page, size));
    }

    @PostMapping("/articles/{articleId}/comments")
    public ApiResponse<CommentThreadResponse> createTopLevel(@PathVariable("articleId") Long articleId,
                                                             @RequestBody @Valid CommentCreateRequest req) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(commentService.createTopLevel(articleId, uid, req));
    }

    @PostMapping("/comments/{commentId}/replies")
    public ApiResponse<CommentThreadResponse> reply(@PathVariable("commentId") Long commentId,
                                                    @RequestBody @Valid CommentCreateRequest req) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(commentService.reply(commentId, uid, req));
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(@PathVariable("commentId") Long commentId) {
        Long uid = currentUser.userId();
        commentService.deleteComment(commentId, uid, currentUser.isAdmin());
        return ApiResponse.ok();
    }
}

