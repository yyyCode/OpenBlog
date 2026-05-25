package com.yqz.openblog.article.service;

import com.yqz.openblog.article.dto.ArticleCreateRequest;
import com.yqz.openblog.article.dto.ArticleDetailResponse;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.dto.ArticleUpdateRequest;
import com.yqz.openblog.article.io.ArticleMarkdownDocument;
import com.yqz.openblog.article.io.ArticleMarkdownExporter;
import com.yqz.openblog.article.io.ArticleMarkdownImporter;
import com.yqz.openblog.common.BizException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ArticleImportExportService {

    private static final long MAX_IMPORT_BYTES = 10L * 1024 * 1024;

    private final ArticleService articleService;

    public ArticleImportExportService(ArticleService articleService) {
        this.articleService = articleService;
    }

    public ArticleListItemResponse importMarkdown(Long userId, MultipartFile file, String mode, Long articleId) {
        byte[] bytes = readBytes(file);
        String filename = file.getOriginalFilename();
        ArticleMarkdownDocument doc = ArticleMarkdownImporter.parse(bytes, filename);

        if ("update".equalsIgnoreCase(mode)) {
            if (articleId == null) {
                throw new BizException(4001, "更新导入需指定文章 ID");
            }
            return articleService.updateArticle(userId, articleId, toUpdateRequest(doc));
        }
        return articleService.createDraft(userId, toCreateRequest(doc));
    }

    public ArticleMarkdownExportPayload exportMarkdown(Long userId, Long articleId, String clientIp) {
        ArticleDetailResponse detail = articleService.detailMine(userId, articleId, clientIp);
        byte[] bytes = ArticleMarkdownExporter.toBytes(detail);
        String filename = ArticleMarkdownExporter.suggestedFilename(detail);
        return new ArticleMarkdownExportPayload(bytes, filename);
    }

    private static ArticleCreateRequest toCreateRequest(ArticleMarkdownDocument doc) {
        ArticleCreateRequest req = new ArticleCreateRequest();
        req.setTitle(doc.getTitle());
        req.setSummary(doc.getSummary());
        req.setContentMarkdown(doc.getContentMarkdown());
        req.setCoverMediaKey(doc.getCoverMediaKey());
        req.setCategoryId(doc.getCategoryId());
        return req;
    }

    private static ArticleUpdateRequest toUpdateRequest(ArticleMarkdownDocument doc) {
        ArticleUpdateRequest req = new ArticleUpdateRequest();
        req.setTitle(doc.getTitle());
        req.setSummary(doc.getSummary());
        req.setContentMarkdown(doc.getContentMarkdown());
        req.setCoverMediaKey(doc.getCoverMediaKey());
        req.setCategoryId(doc.getCategoryId());
        return req;
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(4002, "请上传 Markdown 文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".md")) {
            throw new BizException(4002, "仅支持 .md 文件");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_IMPORT_BYTES) {
                throw new BizException(4002, "Markdown 文件过大");
            }
            return bytes;
        } catch (IOException ex) {
            throw new BizException(4002, "读取文件失败");
        }
    }

    public record ArticleMarkdownExportPayload(byte[] bytes, String filename) {
    }
}
