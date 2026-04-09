package com.codebymike.slidehub.ui.controller;

import com.codebymike.slidehub.ui.service.MgrService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Vista del panel de gestión /mgr (solo DEVELOPER).
 */
@Controller
@RequestMapping("/mgr")
public class MgrController {

    private final MgrService mgrService;

    public MgrController(MgrService mgrService) {
        this.mgrService = mgrService;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("userStats", mgrService.getUserStats());
        model.addAttribute("presStats", mgrService.getPresentationStats());
        model.addAttribute("activeSessions", mgrService.getActiveSessionCount());
        model.addAttribute("users", mgrService.getAllUsers());
        model.addAttribute("presentations", mgrService.getAllPresentations());
        model.addAttribute("health", mgrService.getServicesHealth());
        return "mgr/dashboard";
    }

    @GetMapping("/db")
    public String dbView(Model model) {
        model.addAttribute("tables", mgrService.getAllTables());
        model.addAttribute("usersDetail", mgrService.getTableDetail("users"));
        model.addAttribute("presentationsDetail", mgrService.getTableDetail("presentations"));
        return "mgr/db";
    }
}
