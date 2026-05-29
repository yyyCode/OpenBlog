package com.yqz.openblog.seo.controller;

import com.yqz.openblog.article.dto.ArticleDetailResponse;
import com.yqz.openblog.article.dto.ArticleListItemResponse;
import com.yqz.openblog.article.service.ArticleService;
import com.yqz.openblog.common.PageResult;
import com.yqz.openblog.media.MediaProperties;
import com.yqz.openblog.seo.SeoProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/seo")
public class SeoPageController {

    private final ArticleService articleService;
    private final SeoProperties seoProperties;
    private final MediaProperties mediaProperties;

    public SeoPageController(ArticleService articleService,
                             SeoProperties seoProperties,
                             MediaProperties mediaProperties) {
        this.articleService = articleService;
        this.seoProperties = seoProperties;
        this.mediaProperties = mediaProperties;
    }

    @GetMapping("/article/{id}")
    public String articleDetail(@PathVariable Long id, Model model) {
        ArticleDetailResponse article = articleService.detailPublished(id, "seo-crawler");
        model.addAttribute("article", article);
        model.addAttribute("siteUrl", seoProperties.getSiteUrl());
        model.addAttribute("siteName", seoProperties.getSiteName());
        model.addAttribute("coverUrl", buildCoverUrl(article.getCoverMediaKey()));
        return "seo/article";
    }

    @GetMapping({"", "/"})
    public String home(Model model) {
        PageResult<ArticleListItemResponse> articles = articleService.listPublished(0, 20, null);
        model.addAttribute("articles", articles);
        model.addAttribute("siteUrl", seoProperties.getSiteUrl());
        model.addAttribute("siteName", seoProperties.getSiteName());
        model.addAttribute("siteDescription", seoProperties.getSiteDescription());
        return "seo/home";
    }

    private String buildCoverUrl(String coverMediaKey) {
        if (coverMediaKey == null || coverMediaKey.isBlank()) {
            return null;
        }
        String base = mediaProperties.getPublicBaseUrl();
        if (base == null) base = "";
        return base + "/api/v1/media/files/" + coverMediaKey;
    }
}
