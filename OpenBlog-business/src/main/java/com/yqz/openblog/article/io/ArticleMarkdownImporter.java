package com.yqz.openblog.article.io;

import com.yqz.openblog.common.BizException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 .md 文件文本解析为 {@link ArticleMarkdownDocument}。
 */
public final class ArticleMarkdownImporter {

    private static final Pattern FIRST_H1 = Pattern.compile("(?m)^#\\s+(.+?)\\s*$");
    private static final int MAX_TITLE_LEN = 120;
    private static final int MAX_SUMMARY_LEN = 255;

    private ArticleMarkdownImporter() {
    }

    public static ArticleMarkdownDocument parse(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(4002, "Markdown 文件为空");
        }
        String raw = stripBom(new String(bytes, StandardCharsets.UTF_8));
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');

        String frontMatter = null;
        String body;
        if (normalized.startsWith("---")) {
            int close = normalized.indexOf("\n---", 3);
            if (close > 0) {
                frontMatter = normalized.substring(3, close).trim();
                body = normalized.substring(close + 4);
                if (body.startsWith("\n")) {
                    body = body.substring(1);
                }
            } else {
                body = normalized;
            }
        } else {
            body = normalized;
        }

        ArticleMarkdownDocument doc = new ArticleMarkdownDocument();
        if (frontMatter != null && !frontMatter.isBlank()) {
            applyFrontMatter(doc, frontMatter);
        }

        String content = body == null ? "" : body.strip();
        doc.setContentMarkdown(content);

        if (doc.getTitle() == null || doc.getTitle().isBlank()) {
            doc.setTitle(inferTitle(content, originalFilename));
        }
        doc.setTitle(trimTo(doc.getTitle().trim(), MAX_TITLE_LEN));

        if (doc.getSummary() != null) {
            doc.setSummary(trimTo(doc.getSummary().trim(), MAX_SUMMARY_LEN));
        }

        if (doc.getTitle().isEmpty()) {
            throw new BizException(4002, "无法解析文章标题，请在 Front Matter 中设置 title 或使用 # 标题");
        }
        if (content.isEmpty()) {
            throw new BizException(4002, "Markdown 正文不能为空");
        }
        return doc;
    }

    private static void applyFrontMatter(ArticleMarkdownDocument doc, String yamlBlock) {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(options));
        Object loaded = yaml.load(yamlBlock);
        if (!(loaded instanceof Map<?, ?> map)) {
            return;
        }
        Object title = map.get("title");
        if (title != null) {
            doc.setTitle(String.valueOf(title).trim());
        }
        Object summary = map.get("summary");
        if (summary != null) {
            doc.setSummary(String.valueOf(summary).trim());
        }
        Object categoryId = map.get("categoryId");
        if (categoryId != null) {
            doc.setCategoryId(parseLong(categoryId));
        }
        Object cover = map.get("coverMediaKey");
        if (cover != null) {
            String key = String.valueOf(cover).trim();
            if (!key.isEmpty()) {
                doc.setCoverMediaKey(trimTo(key, 64));
            }
        }
    }

    private static Long parseLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            throw new BizException(4002, "Front Matter 中 categoryId 无效");
        }
    }

    private static String inferTitle(String body, String originalFilename) {
        Matcher m = FIRST_H1.matcher(body);
        if (m.find()) {
            return m.group(1).trim();
        }
        if (originalFilename != null && !originalFilename.isBlank()) {
            String name = originalFilename.trim();
            int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }
            if (name.toLowerCase().endsWith(".md")) {
                name = name.substring(0, name.length() - 3);
            }
            if (!name.isBlank()) {
                return name.trim();
            }
        }
        return "";
    }

    private static String stripBom(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    private static String trimTo(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
