package com.brixo.slidehub.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for legal and public informative pages.
 */
@Controller
public class LegalController {

    @GetMapping("/privacidad")
    public String privacyPolicy() {
        return "legal/privacidad";
    }

    @GetMapping("/politicadeuso")
    public String termsOfUse() {
        return "legal/politicadeuso";
    }

    @GetMapping("/copyright")
    public String copyright() {
        return "legal/copyright";
    }
}
