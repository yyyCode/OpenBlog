package com.yqz.openblog.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.config.SiteProperties;
import com.yqz.openblog.config.ClientIpResolver;
import com.yqz.openblog.site.SiteStatsService;
import com.yqz.openblog.site.SiteVisitService;
import com.yqz.openblog.site.dto.SiteStatsResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/site")
@CrossOrigin(origins = "*")
public class SiteMetaController {

    private final SiteProperties siteProperties;
    private final SiteStatsService siteStatsService;
    private final SiteVisitService siteVisitService;

    public SiteMetaController(SiteProperties siteProperties, SiteStatsService siteStatsService,
                              SiteVisitService siteVisitService) {
        this.siteProperties = siteProperties;
        this.siteStatsService = siteStatsService;
        this.siteVisitService = siteVisitService;
    }

    @GetMapping("/version")
    public ApiResponse<Map<String, String>> version() {
        return ApiResponse.ok(Map.of("version", siteProperties.getVersion()));
    }

    @GetMapping("/stats")
    public ApiResponse<SiteStatsResponse> stats() {
        return ApiResponse.ok(siteStatsService.getPublicStats());
    }

    /**
     * 记录一次全站访问（按 IP 与滑动窗口去重）。前端在应用入口调用一次即可。
     */
    @PostMapping("/visit")
    public ApiResponse<Map<String, Object>> recordVisit(HttpServletRequest request) {
        String clientIp = ClientIpResolver.resolve(request);
        boolean recorded = siteVisitService.tryRecordVisit(clientIp);
        return ApiResponse.ok(Map.of("recorded", recorded));
    }
}
