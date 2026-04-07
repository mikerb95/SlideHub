package com.codebymike.slidehub.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class CheckoutController {

    @GetMapping("/checkout")
    public String checkout() {
        return "public/checkout";
    }

    @PostMapping("/billing/process")
    public String processBilling() {
        // En una implementación real, aquí se procesaría el pago con Stripe u otra pasarela
        return "redirect:/showcase?paid=true";
    }
}
