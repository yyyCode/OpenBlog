package com.yqz.openblog.interview.controller;

import com.yqz.openblog.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class InterviewController {

    /**
     * 预留：面试内容列表接口（暂未开通）。
     */
    @GetMapping("/interviews")
    public ApiResponse<Object> list() {
        return ApiResponse.fail(501, "功能暂未开放");
    }

    /**
     * 预留：创建/提交面试内容（暂未开通）。
     */
    @PostMapping("/interviews")
    public ApiResponse<Object> create() {
        return ApiResponse.fail(501, "功能暂未开放");
    }
}

