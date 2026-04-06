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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consulta la API de UptimeRobot para obtener el estado del monitor de SlideHub.
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
        this.client = WebClient.builder().baseUrl(UPTIMEROBOT_API).build();
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
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (root == null || !"ok".equals(root.path("stat").asText())) {
                return unavailable("Respuesta inesperada de UptimeRobot.");
            }

            JsonNode monitors = root.path("monitors");
            if (!monitors.isArray() || monitors.isEmpty()) {
                return unavailable("No hay monitores registrados en esta cuenta.");
            }

            JsonNode m = monitors.get(0);
            int status = m.path("status").asInt();

            // Uptime ratios (7d-30d-90d)
            String[] ratios = m.path("custom_uptime_ratio").asText("--").split("-");
            String uptime7d  = ratios.length > 0 ? ratios[0] : "--";
            String uptime30d = ratios.length > 1 ? ratios[1] : "--";
            String uptime90d = ratios.length > 2 ? ratios[2] : "--";

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

            // Últimos eventos (logs)
            List<Map<String, String>> incidents = new ArrayList<>();
            JsonNode logs = m.path("logs");
            if (logs.isArray()) {
                for (JsonNode log : logs) {
                    int type = log.path("type").asInt();
                    if (type == 1 || type == 2) { // 1=down, 2=up
                        long ts = log.path("datetime").asLong();
                        int dur = log.path("duration").asInt();
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
            result.put("uptime7d", uptime7d);
            result.put("uptime30d", uptime30d);
            result.put("uptime90d", uptime90d);
            result.put("avgResponseMs", avgResponse);
            result.put("incidents", incidents);
            result.put("checkedAt", DT_FMT.format(Instant.now()));
            return result;

        } catch (Exception e) {
            log.warn("Error consultando UptimeRobot: {}", e.getMessage());
            return unavailable("No se pudo conectar con UptimeRobot.");
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
