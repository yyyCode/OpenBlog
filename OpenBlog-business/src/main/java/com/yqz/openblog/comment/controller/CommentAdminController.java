package com.yqz.openblog.comment.controller;

import com.yqz.openblog.comment.dto.CommentAdminResponse;
import com.yqz.openblog.comment.service.CommentService;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.common.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/comments")
@CrossOrigin(origins = "*")
public class CommentAdminController {

    private final CommentService commentService;

    public CommentAdminController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<CommentAdminResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(commentService.listAllComments(page, size, status));
    }
}
