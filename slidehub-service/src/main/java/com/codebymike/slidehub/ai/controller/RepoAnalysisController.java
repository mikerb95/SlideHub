package com.codebymike.slidehub.ai.controller;

import com.codebymike.slidehub.ai.model.RepoAnalysis;
import com.codebymike.slidehub.ai.service.RepoAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint para análisis técnico de repositorios GitHub (PLAN-EXPANSION.md Fase
 * 3,
 * tarea 33).
 *
 * Cachea los resultados en MongoDB para evitar llamadas repetidas a Gemini.
 * El análisis se reutiliza en la generación de notas (Fase 3) y en el tutor de
 * despliegue (Fase 5).
 */
@RestController
@RequestMapping("/api/ai")
public class RepoAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(RepoAnalysisController.class);

    private final RepoAnalysisService repoAnalysisService;

    public RepoAnalysisController(RepoAnalysisService repoAnalysisService) {
        this.repoAnalysisService = repoAnalysisService;
    }

    /**
     * Analiza un repositorio GitHub y devuelve su análisis técnico completo.
     *
     * Comprueba primero la cache MongoDB; si no existe, llama a Gemini.
     *
     * @param request cuerpo con campo {@code repoUrl}
     */
    @PostMapping("/analyze-repo")
    public ResponseEntity<?> analyzeRepo(@RequestBody Map<String, String> request) {
        String repoUrl = request.get("repoUrl");
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'repoUrl' es obligatorio."));
        }

        try {
            RepoAnalysisService.AnalysisResult result = repoAnalysisService.analyze(repoUrl);
            return ResponseEntity.ok(buildResponse(result));
        } catch (Exception e) {
            if (isGeminiRateLimited(e)) {
                log.warn("Gemini 429 al analizar repositorio {}", repoUrl);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of(
                                "error",
                                "La cuota de Gemini fue excedida (HTTP 429). Espera unos minutos o revisa el plan/facturación de la API.",
                                "code", "GEMINI_QUOTA_EXCEEDED"));
            }
            log.error("Error analizando repositorio {}: {}", repoUrl, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al analizar el repositorio: " + e.getMessage()));
        }
    }

    /**
     * Fuerza un nuevo análisis del repositorio, ignorando la cache MongoDB.
     *
     * @param request cuerpo con campo {@code repoUrl}
     */
    @PostMapping("/analyze-repo/refresh")
    public ResponseEntity<?> reanalyzeRepo(@RequestBody Map<String, String> request) {
        String repoUrl = request.get("repoUrl");
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "El campo 'repoUrl' es obligatorio."));
        }

        try {
            RepoAnalysisService.AnalysisResult result = repoAnalysisService.reanalyze(repoUrl);
            return ResponseEntity.ok(buildResponse(result));
        } catch (Exception e) {
            if (isGeminiRateLimited(e)) {
                log.warn("Gemini 429 al re-analizar repositorio {}", repoUrl);
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of(
                                "error",
                                "La cuota de Gemini fue excedida (HTTP 429). Espera unos minutos o revisa el plan/facturación de la API.",
                                "code", "GEMINI_QUOTA_EXCEEDED"));
            }
            log.error("Error re-analizando repositorio {}: {}", repoUrl, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al re-analizar el repositorio: " + e.getMessage()));
        }
    }

    /**
     * Analiza documentación subida manualmente como archivos multipart.
     * Acepta: .md, .html, .txt, .adoc, .rst, .xml, .json, .yaml, .yml,
     *         .toml, .ini, .properties, .gradle
     * Devuelve el análisis y una {@code syntheticRepoUrl} para el paso 3 del wizard.
     */
    @PostMapping(value = "/analyze-repo/from-docs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyzeFromDocs(
            @RequestPart("files") List<MultipartFile> files) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se recibieron archivos."));
        }

        StringBuilder context = new StringBuilder("Documentación subida manualmente:\n\n");
        int filesRead = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "archivo";
            try {
                byte[] bytes = file.getBytes();
                if (!isTextContent(bytes)) {
                    log.debug("Archivo binario omitido: {}", name);
                    continue;
                }
                String content = new String(bytes, StandardCharsets.UTF_8);
                context.append("=== ").append(name).append(" ===\n");
                // Max 10 KB por archivo para no saturar el contexto de Gemini
                context.append(content, 0, Math.min(content.length(), 10_000));
                context.append("\n\n");
                filesRead++;
            } catch (Exception e) {
                log.warn("No se pudo leer archivo {}: {}", name, e.getMessage());
            }
        }

        if (filesRead == 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No se pudo leer ningún archivo. Asegúrate de subir archivos de texto (.md, .xml, .json, etc.)."));
        }

        try {
            RepoAnalysisService.AnalysisResult result = repoAnalysisService.analyzeFromDocs(context.toString());
            Map<String, Object> response = new HashMap<>(buildResponse(result));
            response.put("syntheticRepoUrl", result.analysis().getRepoUrl());
            response.put("filesRead", filesRead);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            if (isGeminiRateLimited(e)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", "Cuota de Gemini excedida. Intenta más tarde.", "code", "GEMINI_QUOTA_EXCEEDED"));
            }
            log.error("Error analizando docs manuales: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al analizar la documentación: " + e.getMessage()));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildResponse(RepoAnalysisService.AnalysisResult result) {
        RepoAnalysis a = result.analysis();
        Map<String, Object> map = new HashMap<>();
        map.put("language", a.getLanguage());
        map.put("framework", a.getFramework());
        map.put("buildSystem", a.getBuildSystem());
        map.put("technologies", a.getTechnologies());
        map.put("databases", a.getDatabases());
        map.put("ports", a.getPorts());
        map.put("summary", a.getSummary());
        map.put("structure", a.getStructure());
        map.put("deploymentHints", a.getDeploymentHints());
        map.put("filesFound", result.filesFound());
        return map;
    }

    /** Heurística rápida: si los primeros 512 bytes tienen demasiados bytes no-UTF8, es binario. */
    private boolean isTextContent(byte[] bytes) {
        int check = Math.min(bytes.length, 512);
        int nonPrintable = 0;
        for (int i = 0; i < check; i++) {
            int b = bytes[i] & 0xFF;
            if (b < 9 || (b > 13 && b < 32 && b != 27)) nonPrintable++;
        }
        return check == 0 || ((double) nonPrintable / check) < 0.15;
    }

    private boolean isGeminiRateLimited(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WebClientResponseException webClientResponseException
                    && webClientResponseException.getStatusCode().value() == 429) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("HTTP 429")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
