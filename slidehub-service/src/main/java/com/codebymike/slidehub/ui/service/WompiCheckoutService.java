package com.codebymike.slidehub.ui.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class WompiCheckoutService {

    private static final String DEFAULT_CURRENCY = "COP";

    private final WebClient wompiClient;
    private final SecureRandom random;

    @Value("${slidehub.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${slidehub.wompi.public-key:}")
    private String publicKey;

    @Value("${slidehub.wompi.private-key:}")
    private String privateKey;

    @Value("${slidehub.wompi.integrity-secret:}")
    private String integritySecret;

    @Value("${slidehub.wompi.currency:COP}")
    private String currency;

    @Value("${slidehub.wompi.redirect-path:/checkout/result}")
    private String redirectPath;

    public WompiCheckoutService(@Value("${slidehub.wompi.base-url:https://production.wompi.co}") String wompiBaseUrl) {
        this.wompiClient = WebClient.builder()
                .baseUrl(wompiBaseUrl)
                .build();
        this.random = new SecureRandom();
    }

    public Map<String, Object> buildCheckoutData(String plan, String billing, String email, String fullName) {
        String normalizedPlan = "basic".equalsIgnoreCase(plan) ? "basic" : "pro";
        String normalizedBilling = "annual".equalsIgnoreCase(billing) ? "annual" : "monthly";

        if (isBlank(publicKey) || isBlank(integritySecret)) {
            return Map.of(
                    "enabled", false,
                    "message", "Wompi no está configurado. Define Wompi public key e integrity secret en variables de entorno.");
        }

        int amountCop = resolveAmountCop(normalizedPlan, normalizedBilling);
        long amountInCents = amountCop * 100L;
        String usedCurrency = isBlank(currency) ? DEFAULT_CURRENCY : currency.trim().toUpperCase(Locale.ROOT);
        String reference = generateReference(normalizedPlan, normalizedBilling);
        String signature = sha256(reference + amountInCents + usedCurrency + integritySecret.trim());

        Map<String, Object> customerData = new LinkedHashMap<>();
        if (!isBlank(email)) {
            customerData.put("email", email.trim());
        }
        if (!isBlank(fullName)) {
            customerData.put("fullName", fullName.trim());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", true);
        payload.put("publicKey", publicKey.trim());
        payload.put("currency", usedCurrency);
        payload.put("amountInCents", amountInCents);
        payload.put("reference", reference);
        payload.put("redirectUrl", buildRedirectUrl());
        payload.put("signature", Map.of("integrity", signature));
        payload.put("customerData", customerData);
        payload.put("plan", normalizedPlan);
        payload.put("billing", normalizedBilling);
        payload.put("amountCop", amountCop);

        return payload;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> resolveTransaction(String transactionId) {
        if (isBlank(transactionId) || isBlank(privateKey)) {
            return Map.of(
                    "status", "UNKNOWN",
                    "transactionId", transactionId == null ? "" : transactionId,
                    "reference", "");
        }

        try {
            Map<String, Object> response = wompiClient.get()
                    .uri("/v1/transactions/{id}", transactionId.trim())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + privateKey.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                return Map.of(
                        "status", "UNKNOWN",
                        "transactionId", transactionId,
                        "reference", "");
            }

            Object dataObj = response.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                return Map.of(
                        "status", "UNKNOWN",
                        "transactionId", transactionId,
                        "reference", "");
            }

            String status = toStringValue(data.get("status"));
            String reference = toStringValue(data.get("reference"));
            String id = toStringValue(data.get("id"));

            return Map.of(
                    "status", status,
                    "transactionId", id,
                    "reference", reference);
        } catch (Exception ex) {
            return Map.of(
                    "status", "UNKNOWN",
                    "transactionId", transactionId,
                    "reference", "");
        }
    }

    private int resolveAmountCop(String plan, String billing) {
        int monthlyAmount = "basic".equals(plan) ? 6900 : 15900;
        int annualAmount = "basic".equals(plan) ? 69000 : 159000;
        return "annual".equals(billing) ? annualAmount : monthlyAmount;
    }

    private String buildRedirectUrl() {
        String normalizedBase = (baseUrl == null ? "" : baseUrl.trim()).replaceAll("/$", "");
        String normalizedPath = redirectPath == null || redirectPath.isBlank() ? "/checkout/result" : redirectPath.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return normalizedBase + normalizedPath;
    }

    private String generateReference(String plan, String billing) {
        String randomSuffix = String.format("%06d", random.nextInt(1_000_000));
        return "SH-" + plan.toUpperCase(Locale.ROOT) + "-" + billing.toUpperCase(Locale.ROOT) + "-" +
                Instant.now().toEpochMilli() + "-" + randomSuffix;
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo generar la firma de integridad para Wompi.", ex);
        }
    }

    private String toStringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
