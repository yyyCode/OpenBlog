package com.yqz.openblog.article.io;

import com.yqz.openblog.article.dto.ArticleDetailResponse;

/**
 * 将文章详情序列化为带 YAML Front Matter 的 Markdown 文件内容。
 */
public final class ArticleMarkdownExporter {

    private ArticleMarkdownExporter() {
    }

    public static byte[] toBytes(ArticleDetailResponse detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(yamlQuote(detail.getTitle())).append('\n');
        if (detail.getSummary() != null && !detail.getSummary().isBlank()) {
            sb.append("summary: ").append(yamlQuote(detail.getSummary())).append('\n');
        }
        if (detail.getCategoryId() != null) {
            sb.append("categoryId: ").append(detail.getCategoryId()).append('\n');
        }
        if (detail.getCoverMediaKey() != null && !detail.getCoverMediaKey().isBlank()) {
            sb.append("coverMediaKey: ").append(yamlQuote(detail.getCoverMediaKey())).append('\n');
        }
        if (detail.getId() != null) {
            sb.append("openblogId: ").append(detail.getId()).append('\n');
        }
        if (detail.getPublishedAt() != null) {
            sb.append("publishedAt: ").append(yamlQuote(detail.getPublishedAt().toString())).append('\n');
        }
        sb.append("---\n\n");
        String body = detail.getContentMarkdown() == null ? "" : detail.getContentMarkdown();
        sb.append(body);
        if (!body.endsWith("\n")) {
            sb.append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String suggestedFilename(ArticleDetailResponse detail) {
        Long id = detail.getId();
        String title = detail.getTitle();
        String base;
        if (title == null || title.isBlank()) {
            base = "article-" + (id == null ? "export" : id);
        } else {
            base = title.trim();
        }
        base = base.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ").strip();
        if (base.isEmpty()) {
            base = "article-" + (id == null ? "export" : id);
        }
        if (base.length() > 80) {
            base = base.substring(0, 80).strip();
        }
        return base + ".md";
    }

    private static String yamlQuote(String value) {
        if (value == null) {
            return "\"\"";
        }
        boolean needQuote = value.contains(":") || value.contains("#")
                || value.startsWith(" ") || value.endsWith(" ")
                || value.contains("\n") || value.contains("\"");
        if (!needQuote) {
            return value;
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
