package com.codebymike.slidehub.ui.controller;

import com.codebymike.slidehub.ui.service.UptimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class DocsController {

    private final UptimeService uptimeService;

    public DocsController(UptimeService uptimeService) {
        this.uptimeService = uptimeService;
    }

    @GetMapping("/ai-guide")
    public String aiGuide() {
        return "ai-guide";
    }

    @GetMapping("/calidad")
    public String qualityGuide() {
        return "calidad";
    }

    @GetMapping("/matriz-pdca")
    public String matrizPdca() {
        return "matriz-pdca";
    }

    @GetMapping("/metodologia")
    public String metodologia() {
        return "metodologia";
    }

    @GetMapping("/sustentacion")
    public String sustentacion() {
        return "sustentacion";
    }

    @GetMapping("/uptime")
    public String uptime() {
        return "uptime";
    }

    @GetMapping("/api/uptime/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uptimeStatus() {
        return ResponseEntity.ok(uptimeService.getStatus());
    }
}
