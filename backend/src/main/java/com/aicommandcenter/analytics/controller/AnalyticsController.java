package com.aicommandcenter.analytics.controller;

import com.aicommandcenter.analytics.dto.AnalyticsResponse;
import com.aicommandcenter.analytics.service.AnalyticsService;
import com.aicommandcenter.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public ResponseEntity<AnalyticsResponse> analytics(@RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.build(SecurityUtils.currentUserId(), days));
    }
}
