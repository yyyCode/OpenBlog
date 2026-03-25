package com.yqz.openblog.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.config.SiteProperties;
import com.yqz.openblog.site.SiteStatsService;
import com.yqz.openblog.site.dto.SiteStatsResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/site")
@CrossOrigin(origins = "*")
public class SiteMetaController {

    private final SiteProperties siteProperties;
    private final SiteStatsService siteStatsService;

    public SiteMetaController(SiteProperties siteProperties, SiteStatsService siteStatsService) {
        this.siteProperties = siteProperties;
        this.siteStatsService = siteStatsService;
    }

    @GetMapping("/version")
    public ApiResponse<Map<String, String>> version() {
        return ApiResponse.ok(Map.of("version", siteProperties.getVersion()));
    }

    @GetMapping("/stats")
    public ApiResponse<SiteStatsResponse> stats() {
        return ApiResponse.ok(siteStatsService.getPublicStats());
    }
}
