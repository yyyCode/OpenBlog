package com.yqz.openblog.seo.service;

import com.yqz.openblog.seo.SeoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
public class BaiduPushService {

    private static final Logger log = LoggerFactory.getLogger(BaiduPushService.class);
    private static final String BAIDU_PUSH_API = "http://data.zz.baidu.com/urls";

    private final SeoProperties seoProperties;
    private final HttpClient httpClient;

    public BaiduPushService(SeoProperties seoProperties) {
        this.seoProperties = seoProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Async
    public void pushUrls(List<String> urls) {
        if (!seoProperties.isBaiduPushEnabled()) {
            return;
        }
        String token = seoProperties.getBaiduPushToken();
        if (token == null || token.isBlank()) {
            return;
        }
        if (urls == null || urls.isEmpty()) {
            return;
        }

        String body = String.join("\n", urls);
        String apiUrl = BAIDU_PUSH_API + "?site=" + seoProperties.getSiteUrl() + "&token=" + token;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Baidu push response [{}]: {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.warn("Baidu push failed: {}", e.getMessage());
        }
    }

    public void pushArticleUrl(Long articleId) {
        String url = seoProperties.getSiteUrl() + "/article/" + articleId;
        pushUrls(List.of(url));
    }
}
