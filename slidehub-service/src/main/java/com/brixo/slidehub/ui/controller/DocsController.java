package com.brixo.slidehub.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocsController {

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
}
