package com.codebymike.slidehub.ui.controller;

import com.codebymike.slidehub.ui.model.Presentation;
import com.codebymike.slidehub.ui.repository.PresentationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Vista pública /join — permite al público acceder a una presentación en modo
 * espectador usando su código de 5 dígitos.
 */
@Controller
@RequestMapping("/join")
public class JoinController {

    private final PresentationRepository presentationRepository;

    public JoinController(PresentationRepository presentationRepository) {
        this.presentationRepository = presentationRepository;
    }

    @GetMapping
    public String joinForm(@RequestParam(name = "code", required = false) String code, Model model) {
        if (code != null) {
            model.addAttribute("code", code);
        }
        return "join";
    }

    @PostMapping
    public String joinPresentation(@RequestParam("code") String code, Model model) {
        if (code == null || !code.matches("\\d{5}")) {
            model.addAttribute("error", "Ingresa un código válido de 5 dígitos.");
            model.addAttribute("code", code);
            return "join";
        }

        Optional<Presentation> presentation = presentationRepository.findByJoinCode(code);
        if (presentation.isEmpty()) {
            model.addAttribute("error", "Código no encontrado. Verifica e intenta nuevamente.");
            model.addAttribute("code", code);
            return "join";
        }

        return "redirect:/slides?presentationId=" + presentation.get().getId();
    }
}
