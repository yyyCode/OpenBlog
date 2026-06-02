package com.yqz.openblog.seo.controller;

import com.yqz.openblog.seo.SeoProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RobotsController {

    private final SeoProperties seoProperties;

    public RobotsController(SeoProperties seoProperties) {
        this.seoProperties = seoProperties;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return "User-agent: *\n" +
                "Allow: /\n" +
                "Allow: /article/\n" +
                "Allow: /all\n" +
                "Allow: /changelog\n" +
                "Disallow: /console/\n" +
                "Disallow: /login\n" +
                "Disallow: /api/\n" +
                "\n" +
                "Sitemap: " + seoProperties.getSiteUrl() + "/sitemap.xml\n";
    }
}
