package com.yqz.openblog.article.service;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class MarkdownRenderer {

    private static final Pattern NON_WHITESPACE = Pattern.compile("\\S");

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create()
        ));
        options.set(HtmlRenderer.SOFT_BREAK, "\n");
        options.set(HtmlRenderer.ESCAPE_HTML, false);
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return renderer.render(parser.parse(markdown));
    }

    public int estimateWordCount(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        String stripped = markdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`[^`]*`", " ")
                .replaceAll("!\\[.*?]\\(.*?\\)", " ")
                .replaceAll("\\[.*?]\\(.*?\\)", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("(?m)^#{1,6}\\s+", " ")
                .replaceAll("[*_~>|-]", " ");
        int count = 0;
        java.util.regex.Matcher m = NON_WHITESPACE.matcher(stripped);
        while (m.find()) {
            count++;
        }
        return Math.max(1, count / 2);
    }
}
