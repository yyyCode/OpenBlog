package com.yqz.openblog.article.controller;

import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.service.ArticleImportExportService;
import com.yqz.openblog.article.service.ArticleImportExportService.ArticleMarkdownExportPayload;
import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.config.ClientIpResolver;
import com.yqz.openblog.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class ArticleImportExportController {

    private final ArticleImportExportService importExportService;
    private final CurrentUser currentUser;

    public ArticleImportExportController(ArticleImportExportService importExportService, CurrentUser currentUser) {
        this.importExportService = importExportService;
        this.currentUser = currentUser;
    }

    @PostMapping(value = "/articles/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ApiResponse<ArticleListItemResponse> importMarkdown(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "create") String mode,
            @RequestParam(value = "articleId", required = false) Long articleId) {
        Long uid = currentUser.userId();
        return ApiResponse.ok(importExportService.importMarkdown(uid, file, mode, articleId));
    }

    @GetMapping(value = "/users/me/articles/{articleId}/export", produces = "text/markdown")
    @PreAuthorize("hasAnyRole('ADMIN','AUTHOR')")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable("articleId") Long articleId,
                                                 HttpServletRequest request) {
        Long uid = currentUser.userId();
        String clientIp = ClientIpResolver.resolve(request);
        ArticleMarkdownExportPayload payload = importExportService.exportMarkdown(uid, articleId, clientIp);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(payload.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(payload.bytes());
    }
}
