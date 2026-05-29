package com.yqz.openblog.seo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yqz.openblog.article.entity.Article;
import com.yqz.openblog.article.entity.ArticleStatus;
import com.yqz.openblog.article.repo.ArticleMapper;
import com.yqz.openblog.seo.SeoProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
public class SitemapController {

    private final ArticleMapper articleMapper;
    private final SeoProperties seoProperties;

    public SitemapController(ArticleMapper articleMapper, SeoProperties seoProperties) {
        this.articleMapper = articleMapper;
        this.seoProperties = seoProperties;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        LambdaQueryWrapper<Article> w = Wrappers.lambdaQuery();
        w.eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .select(Article::getId, Article::getUpdatedAt, Article::getPublishedAt)
                .orderByDesc(Article::getPublishedAt);

        List<Article> articles = articleMapper.selectList(w);
        String siteUrl = seoProperties.getSiteUrl();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_DATE.withZone(ZoneOffset.UTC);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        sb.append("  <url>\n");
        sb.append("    <loc>").append(siteUrl).append("/</loc>\n");
        sb.append("    <changefreq>daily</changefreq>\n");
        sb.append("    <priority>1.0</priority>\n");
        sb.append("  </url>\n");

        for (Article a : articles) {
            sb.append("  <url>\n");
            sb.append("    <loc>").append(siteUrl).append("/article/").append(a.getId()).append("</loc>\n");
            if (a.getUpdatedAt() != null) {
                sb.append("    <lastmod>").append(fmt.format(a.getUpdatedAt())).append("</lastmod>\n");
            } else if (a.getPublishedAt() != null) {
                sb.append("    <lastmod>").append(fmt.format(a.getPublishedAt())).append("</lastmod>\n");
            }
            sb.append("    <changefreq>weekly</changefreq>\n");
            sb.append("    <priority>0.8</priority>\n");
            sb.append("  </url>\n");
        }

        sb.append("</urlset>");
        return sb.toString();
    }
}
