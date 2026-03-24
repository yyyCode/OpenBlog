package com.yqz.openblog.controller;

import com.yqz.openblog.common.ApiResponse;
import com.yqz.openblog.config.SiteProperties;
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

    public SiteMetaController(SiteProperties siteProperties) {
        this.siteProperties = siteProperties;
    }

    @GetMapping("/version")
    public ApiResponse<Map<String, String>> version() {
        return ApiResponse.ok(Map.of("version", siteProperties.getVersion()));
    }
}
