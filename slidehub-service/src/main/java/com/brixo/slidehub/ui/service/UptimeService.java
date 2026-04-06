package com.brixo.slidehub.ui.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consulta la API de UptimeRobot v2 para obtener el estado del monitor de SlideHub.
 * Toda la comunicación es vía HTTP con WebClient — sin SDK de terceros.
 */
@Service
public class UptimeService {

    private static final Logger log = LoggerFactory.getLogger(UptimeService.class);
    private static final String UPTIMEROBOT_API = "https://api.uptimerobot.com";
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.of("America/Bogota"));

    private final WebClient client;

    @Value("${slidehub.uptimerobot.api-key:}")
    private String apiKey;

    public UptimeService() {
        this.client = WebClient.builder()
                .baseUrl(UPTIMEROBOT_API)
                .defaultHeader("Cache-Control", "no-cache")
                .build();
    }

    /**
     * Devuelve el estado del primer monitor asociado a la API key.
     * Si la key no está configurada o la llamada falla, devuelve un mapa con
     * {@code available=false} para que el frontend lo maneje con gracia.
     */
    public Map<String, Object> getStatus() {
        if (apiKey == null || apiKey.isBlank()) {
            return unavailable("UPTIMEROBOT_API_KEY no configurada.");
        }

        try {
            // BodyInserters.fromFormData ya setea Content-Type: application/x-www-form-urlencoded
            // NO agregar el header manualmente o se duplica y corrompe la request
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("api_key", apiKey);
            form.add("format", "json");
            form.add("response_times", "1");
            form.add("response_times_limit", "10");
            form.add("custom_uptime_ratios", "7-30-90");
            form.add("logs", "1");
            form.add("logs_limit", "5");

            JsonNode root = client.post()
                    .uri("/v2/getMonitors")
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root == null) {
                return unavailable("Respuesta vacía de UptimeRobot.");
            }

            String stat = root.path("stat").asText();
            if (!"ok".equals(stat)) {
                String errorMsg = root.path("error").path("message").asText("Error desconocido");
                log.warn("UptimeRobot respondió stat={}: {}", stat, errorMsg);
                return unavailable("UptimeRobot: " + errorMsg);
            }

            JsonNode monitors = root.path("monitors");
            if (!monitors.isArray() || monitors.isEmpty()) {
                return unavailable("No hay monitores en esta cuenta.");
            }

            JsonNode m = monitors.get(0);
            int status = m.path("status").asInt();

            // Uptime ratios — formato "99.350-99.128-99.128" (7d-30d-90d)
            String[] ratios = m.path("custom_uptime_ratio").asText("").split("-");
            String uptime7d  = parseRatio(ratios, 0);
            String uptime30d = parseRatio(ratios, 1);
            String uptime90d = parseRatio(ratios, 2);

            // Tiempo de respuesta promedio
            long avgResponse = 0;
            JsonNode times = m.path("response_times");
            if (times.isArray() && !times.isEmpty()) {
                long sum = 0;
                int count = 0;
                for (JsonNode t : times) {
                    sum += t.path("value").asLong();
                    count++;
                }
                avgResponse = count > 0 ? sum / count : 0;
            }

            // Últimos eventos (type 1=down, 2=up)
            List<Map<String, String>> incidents = new ArrayList<>();
            JsonNode logs = m.path("logs");
            if (logs.isArray()) {
                for (JsonNode entry : logs) {
                    int type = entry.path("type").asInt();
                    if (type == 1 || type == 2) {
                        long ts  = entry.path("datetime").asLong();
                        int  dur = entry.path("duration").asInt();
                        Map<String, String> incident = new HashMap<>();
                        incident.put("type", type == 1 ? "DOWN" : "UP");
                        incident.put("datetime", DT_FMT.format(Instant.ofEpochSecond(ts)));
                        incident.put("duration", dur > 0 ? formatDuration(dur) : "—");
                        incidents.add(incident);
                    }
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("available", true);
            result.put("monitorName", m.path("friendly_name").asText("SlideHub"));
            result.put("url", m.path("url").asText(""));
            result.put("status", status);
            result.put("statusLabel", resolveLabel(status));
            result.put("statusColor", resolveColor(status));
            result.put("uptime7d",  uptime7d);
            result.put("uptime30d", uptime30d);
            result.put("uptime90d", uptime90d);
            result.put("avgResponseMs", avgResponse);
            result.put("incidents", incidents);
            result.put("checkedAt", DT_FMT.format(Instant.now()));
            return result;

        } catch (WebClientResponseException e) {
            log.warn("UptimeRobot HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return unavailable("Error HTTP " + e.getStatusCode().value() + " de UptimeRobot.");
        } catch (Exception e) {
            log.warn("Error consultando UptimeRobot: {}", e.getMessage());
            return unavailable("No se pudo conectar con UptimeRobot.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extrae y formatea un ratio del array de uptime.
     * Si el valor es 0.000 (monitor recién creado, sin datos suficientes) devuelve null
     * para que el frontend muestre "Sin datos" en lugar de "0.00%".
     */
    private String parseRatio(String[] parts, int index) {
        if (index >= parts.length) return null;
        String raw = parts[index].trim();
        if (raw.isEmpty()) return null;
        try {
            double val = Double.parseDouble(raw);
            return val <= 0.0 ? null : raw;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> unavailable(String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("available", false);
        m.put("reason", reason);
        return m;
    }

    private String resolveLabel(int status) {
        return switch (status) {
            case 2 -> "OPERATIVO";
            case 8 -> "DEGRADADO";
            case 9 -> "CAÍDO";
            case 0 -> "PAUSADO";
            default -> "VERIFICANDO";
        };
    }

    private String resolveColor(int status) {
        return switch (status) {
            case 2 -> "green";
            case 8 -> "amber";
            case 9 -> "red";
            default -> "gray";
        };
    }

    private String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}
