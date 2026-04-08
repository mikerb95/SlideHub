package com.codebymike.slidehub.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CheckoutController {

    @GetMapping("/checkout")
    public String checkout(@RequestParam(name = "plan", defaultValue = "pro") String plan,
            @RequestParam(name = "billing", defaultValue = "monthly") String billing,
            Model model) {
        String normalizedPlan = "basic".equalsIgnoreCase(plan) ? "basic" : "pro";
        String normalizedBilling = "annual".equalsIgnoreCase(billing) ? "annual" : "monthly";

        String planLabel = "basic".equals(normalizedPlan) ? "Plan Básico" : "Plan Pro";
        int monthlyAmount = "basic".equals(normalizedPlan) ? 6900 : 15900;
        int annualAmount = "basic".equals(normalizedPlan) ? 69000 : 159000;

        int subtotal = "annual".equals(normalizedBilling) ? annualAmount : monthlyAmount;
        int annualDiscount = Math.max(0, (monthlyAmount * 12) - annualAmount);

        model.addAttribute("planCode", normalizedPlan);
        model.addAttribute("billingCode", normalizedBilling);
        model.addAttribute("planLabel", planLabel);
        model.addAttribute("billingLabel", "annual".equals(normalizedBilling) ? "Facturación anual" : "Facturación mensual");
        model.addAttribute("displayAmount", formatCop(subtotal));
        model.addAttribute("displayPeriod", "annual".equals(normalizedBilling) ? "COP/año" : "COP/mes");
        model.addAttribute("annualDiscount", annualDiscount);
        model.addAttribute("displayAnnualDiscount", formatCop(annualDiscount));

        return "public/checkout";
    }

    @PostMapping("/billing/process")
    public String processBilling(@RequestParam(name = "plan", defaultValue = "pro") String plan,
            @RequestParam(name = "billing", defaultValue = "monthly") String billing) {
        // En una implementación real, aquí se procesaría el pago con Stripe u otra pasarela
        return "redirect:/showcase?paid=true&plan=" + plan + "&billing=" + billing;
    }

    private String formatCop(int amount) {
        return "$" + String.format("%,d", amount).replace(',', '.');
    }
}
