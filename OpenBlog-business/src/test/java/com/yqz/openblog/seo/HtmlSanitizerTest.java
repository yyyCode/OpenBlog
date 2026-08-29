package com.yqz.openblog.seo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEO 渲染路径的 HTML 净化回归测试：脚本/事件/危险协议必须被剥离，正常排版保留。
 */
class HtmlSanitizerTest {

    @Test
    void stripsScriptAndEventHandlers() {
        String cleaned = HtmlSanitizer.clean(
                "<p>正常<b>加粗</b></p><script>alert(1)</script><img src=x onerror=alert(1)>");
        assertThat(cleaned).contains("正常").contains("<b>加粗</b>");
        assertThat(cleaned).doesNotContain("<script").doesNotContain("onerror");
    }

    @Test
    void stripsJavascriptProtocolLinks() {
        assertThat(HtmlSanitizer.clean("<a href=\"javascript:alert(1)\">x</a>"))
                .doesNotContain("javascript:");
    }

    @Test
    void keepsSafeTableAndLink() {
        String html = "<table><tr><td><a href=\"https://a.com\">link</a></td></tr></table>";
        String cleaned = HtmlSanitizer.clean(html);
        assertThat(cleaned).contains("<a").contains("https://a.com").contains("<td>");
    }

    @Test
    void nullAndEmptyPassThrough() {
        assertThat(HtmlSanitizer.clean(null)).isNull();
        assertThat(HtmlSanitizer.clean("")).isEmpty();
    }
}
