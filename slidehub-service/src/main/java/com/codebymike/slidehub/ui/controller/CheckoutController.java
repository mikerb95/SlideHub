package com.codebymike.slidehub.ui.controller;

import com.codebymike.slidehub.ui.service.WompiCheckoutService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.Map;

@Controller
public class CheckoutController {

    private final WompiCheckoutService wompiCheckoutService;

    public CheckoutController(WompiCheckoutService wompiCheckoutService) {
        this.wompiCheckoutService = wompiCheckoutService;
    }

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
        model.addAttribute("billingLabel",
                "annual".equals(normalizedBilling) ? "Facturación anual" : "Facturación mensual");
        model.addAttribute("displayAmount", formatCop(subtotal));
        model.addAttribute("displayPeriod", "annual".equals(normalizedBilling) ? "COP/año" : "COP/mes");
        model.addAttribute("annualDiscount", annualDiscount);
        model.addAttribute("displayAnnualDiscount", formatCop(annualDiscount));

        return "public/checkout";
    }

    @PostMapping("/billing/process")
    public String processBilling(@RequestParam(name = "plan", defaultValue = "pro") String plan,
            @RequestParam(name = "billing", defaultValue = "monthly") String billing) {
        // En una implementación real, aquí se procesaría el pago con Stripe u otra
        // pasarela
        return "redirect:/showcase?paid=true&plan=" + plan + "&billing=" + billing;
    }

    @PostMapping("/api/billing/wompi/checkout-data")
    @ResponseBody
    public Map<String, Object> wompiCheckoutData(@RequestBody Map<String, String> request) {
        String plan = request.getOrDefault("plan", "pro");
        String billing = request.getOrDefault("billing", "monthly");
        String email = request.getOrDefault("email", "");
        String fullName = request.getOrDefault("fullName", "");
        return wompiCheckoutService.buildCheckoutData(plan, billing, email, fullName);
    }

    @GetMapping("/checkout/result")
    public String checkoutResult(@RequestParam Map<String, String> query,
            RedirectAttributes redirectAttributes) {
        String transactionId = firstNonBlank(query.get("id"), query.get("transaction_id"));
        String status = firstNonBlank(query.get("status"), "UNKNOWN");
        String reference = firstNonBlank(query.get("reference"), "");

        if (!transactionId.isBlank()) {
            Map<String, String> transaction = wompiCheckoutService.resolveTransaction(transactionId);
            status = firstNonBlank(transaction.get("status"), status);
            reference = firstNonBlank(transaction.get("reference"), reference);
        }

        boolean approved = "APPROVED".equalsIgnoreCase(status);

        redirectAttributes.addAttribute("paid", approved);
        redirectAttributes.addAttribute("paymentProvider", "wompi");
        redirectAttributes.addAttribute("paymentStatus", status.toLowerCase(Locale.ROOT));
        if (!transactionId.isBlank()) {
            redirectAttributes.addAttribute("transactionId", transactionId);
        }
        if (!reference.isBlank()) {
            redirectAttributes.addAttribute("reference", reference);
        }
        return "redirect:/showcase";
    }

    private String formatCop(int amount) {
        return "$" + String.format("%,d", amount).replace(',', '.');
    }

    private String firstNonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback == null ? "" : fallback;
    }
}
