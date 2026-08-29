package com.yqz.openblog.seo;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * 服务端 HTML 净化器（SEO 渲染路径用）。
 *
 * <p>背景：markdown 渲染器 {@code MarkdownRenderer} 显式 {@code ESCAPE_HTML=false}，原始 HTML
 * 会原样进入 {@code content_html} 入库。Vue 前端在 v-html 前用 DOMPurify 清洗，但 SEO 模板
 * （templates/seo/article.html）用 {@code th:utext} 非转义输出，是唯一未净化的公开渲染面，
 * 若正文含 {@code <script>} / {@code <img onerror>} 即构成存储型 XSS。
 *
 * <p>本类在 SEO 渲染边界清洗 {@code content_html}（不改动存储与 API 返回，前端行为不变），
 * 同时覆盖已入库的旧脏数据。
 */
public final class HtmlSanitizer {

    /** 允许的标签/属性白名单：剥离 script/style/iframe/svg 等可执行标签、on* 事件与 javascript: 协议。 */
    private static final Safelist SAFELIST = Safelist.relaxed();

    private HtmlSanitizer() {
    }

    public static String clean(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
