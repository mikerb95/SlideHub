package com.codebymike.slidehub.ai.service;

import com.codebymike.slidehub.ai.model.NoteContent;
import com.codebymike.slidehub.ai.model.PresenterNote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cliente HTTP para Groq API — generación de notas del presentador (CLAUDE.md
 * §9.2,
 * PLAN-EXPANSION.md Fase 3, tarea 29).
 *
 * Integración exclusivamente vía HTTP / WebClient — sin SDK de Groq.
 * Usa la API compatible con OpenAI de Groq:
 * {@code POST /openai/v1/chat/completions}.
 */
@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    private final WebClient groqClient;
    private final ObjectMapper objectMapper;

    @Value("${slidehub.ai.groq.api-key}")
    private String apiKey;

    @Value("${slidehub.ai.groq.model}")
    private String model;

    @Value("${slidehub.ai.groq.fallback-models:llama-3.1-8b-instant,llama-3.3-70b-versatile}")
    private String fallbackModels;

    public GroqService(@Value("${slidehub.ai.groq.base-url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.groqClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    // ── Generación de notas del presentador ───────────────────────────────────

    /**
     * Genera notas estructuradas del presentador para un slide (PLAN-EXPANSION.md
     * Fase 3, tarea 29).
     *
     * @param repoContext      contexto técnico extraído del repositorio por Gemini
     * @param slideDescription descripción del slide (de Vision o fallback textual)
     * @param slideNumber      número 1-based del slide
     * @return notas estructuradas parseadas desde la respuesta JSON de Groq
     */
    public NoteContent generateNote(String repoContext, String slideDescription, int slideNumber) {
        String prompt = buildNotePrompt(repoContext, slideDescription, slideNumber);

        String systemMessage = "Eres un asistente que genera notas de presentación en español. "
                + "Responde SIEMPRE en JSON válido, sin markdown ni texto adicional.";
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemMessage),
                Map.of("role", "user", "content", prompt));

        Exception lastException = null;
        String lastErrorDetail = "";

        for (String candidateModel : resolveCandidateModels()) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    Map<?, ?> response = callGroqChatCompletion(candidateModel, messages, 0.7, 1024);
                    String rawJson = extractMessageContent(response);
                    rawJson = stripMarkdownJson(rawJson);
                    log.debug("Groq generateNote respondió (slide {}, model {}): {} chars",
                            slideNumber, candidateModel, rawJson.length());
                    return objectMapper.readValue(rawJson, NoteContent.class);
                } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                    lastException = e;
                    lastErrorDetail = e.getResponseBodyAsString();

                    if (isModelDecommissioned(lastErrorDetail)) {
                        log.warn("Modelo Groq descontinuado ({}). Probando fallback para slide {}.",
                                candidateModel, slideNumber);
                        break;
                    }

                    if (attempt < 2) {
                        log.warn("Fallo Groq en slide {} con modelo {} (intento {}/2). Reintentando...",
                                slideNumber, candidateModel, attempt);
                        sleepBeforeRetry();
                        continue;
                    }

                    log.error("Error de Groq en slide {} con modelo {}: {}",
                            slideNumber, candidateModel, lastErrorDetail);
                } catch (Exception e) {
                    lastException = e;
                    lastErrorDetail = e.getMessage() != null ? e.getMessage() : "Error desconocido";

                    if (attempt < 2) {
                        log.warn("Fallo Groq en slide {} con modelo {} (intento {}/2). Reintentando...",
                                slideNumber, candidateModel, attempt);
                        sleepBeforeRetry();
                        continue;
                    }

                    log.error("Error generando nota con Groq (slide {}, model {}): {}",
                            slideNumber, candidateModel, lastErrorDetail);
                }
            }
        }

        if (lastException != null) {
            return new NoteContent(
                    "Slide " + slideNumber,
                    List.of("No se pudo generar la nota con IA. Detalle: " + lastErrorDetail),
                    "~2 min",
                    List.of(),
                    List.of());
        }

        return new NoteContent(
                "Slide " + slideNumber,
                List.of("No se pudo generar la nota con IA. Intenta de nuevo en unos segundos."),
                "~2 min",
                List.of(),
                List.of());
    }

    /**
     * Transcribe audio bytes using Groq Whisper-compatible endpoint.
     */
    @SuppressWarnings("unchecked")
    public String transcribeAudio(byte[] audioBytes, String filename, String contentType) {
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Audio vacío.");
        }

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("model", "whisper-large-v3-turbo");
        builder.part("response_format", "verbose_json");
        builder.part("language", "es");
        builder.part("file", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return filename != null && !filename.isBlank() ? filename : "audio.webm";
            }
        }).contentType(MediaType.parseMediaType(contentType != null && !contentType.isBlank()
                ? contentType
                : "audio/webm"));

        try {
            Map<String, Object> response = groqClient.post()
                    .uri("/openai/v1/audio/transcriptions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .bodyValue(builder.build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                return "";
            }
            Object text = response.get("text");
            return text != null ? text.toString().trim() : "";
        } catch (Exception ex) {
            log.error("Error transcribiendo audio en Groq: {}", ex.getMessage());
            throw new RuntimeException("No se pudo transcribir el audio.", ex);
        }
    }

    /**
     * Generates a concise answer for an audience question using extracted repo
     * context.
     */
    public String answerAudienceQuestion(String transcription, String repoContext, String slideContext) {
        String prompt = """
                Contexto del slide: %s

                Contexto técnico del repositorio:
                %s

                Pregunta del público (transcripción):
                %s

                Responde en español, máximo 4 frases, tono claro y práctico para exposición técnica.
                Si falta contexto específico, indica una suposición breve y ofrece una respuesta útil.
                """.formatted(
                slideContext != null ? slideContext : "",
                repoContext != null ? repoContext : "",
                transcription != null ? transcription : "");

        String answer = callGroqRaw(prompt,
                "Eres un asistente técnico para expositores. Da respuestas cortas, concretas y útiles para responder preguntas del público.");
        answer = stripMarkdownCode(answer);
        if (answer.isBlank()) {
            return "No tengo suficiente contexto para responder con precisión, pero puedes explicar el objetivo del módulo y su flujo principal.";
        }
        return answer;
    }

    // ── Expansión de notas ────────────────────────────────────────────────────

    /**
     * Genera una explicación expandida de las notas del presentador para un slide.
     * Pensado para ser consultado desde el control remoto cuando el participante
     * presiona "Expandir info". El resultado se cachea en MongoDB.
     *
     * @param note la nota a expandir (debe tener al menos título y puntos)
     * @return texto expandido en español (3-5 párrafos)
     */
    public String expandNote(PresenterNote note) {
        String pointsList = note.getPoints() != null
                ? note.getPoints().stream().map(p -> "- " + p).reduce("", (a, b) -> a + "\n" + b).strip()
                : "";
        String phrases = note.getKeyPhrases() != null ? String.join(", ", note.getKeyPhrases()) : "";

        String prompt = """
                Tienes las siguientes notas del presentador para el slide "%s":

                Puntos clave:
                %s

                Frases clave: %s

                Genera una explicación expandida en español (3-5 párrafos) que profundice en cada punto.
                La explicación debe ser útil para que quien la lea entienda el tema y pueda responder preguntas del público.
                Sé técnico pero claro. Responde SOLO el texto continuo, sin listas, sin JSON ni markdown.
                """
                .formatted(
                        note.getTitle() != null ? note.getTitle() : "Slide " + note.getSlideNumber(),
                        pointsList,
                        phrases);

        String result = callGroqRaw(prompt,
                "Eres un asistente técnico para presentadores. Genera explicaciones claras y útiles en español.");
        result = stripMarkdownCode(result);
        return result.isBlank()
                ? "No se pudo generar la explicación expandida."
                : result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildNotePrompt(String repoContext, String slideDescription, int slideNumber) {
        StringBuilder sb = new StringBuilder();

        if (slideDescription != null && !slideDescription.isBlank()) {
            sb.append("Descripción del slide %d: %s\n\n".formatted(slideNumber, slideDescription));
        }

        if (repoContext != null && !repoContext.isBlank()) {
            sb.append("Contexto técnico del repositorio:\n%s\n\n".formatted(repoContext));
        }

        sb.append("""
                Genera notas del presentador en JSON exactamente con este formato (sin texto extra):
                {
                  "title": "Título corto y descriptivo del slide",
                  "points": ["punto técnico 1", "punto técnico 2", "punto técnico 3"],
                  "suggestedTime": "~2 min",
                  "keyPhrases": ["frase clave 1", "frase clave 2"],
                  "demoTags": ["demo-tag-1"]
                }
                """);

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractMessageContent(Map<?, ?> response) {
        if (response == null)
            return "{}";
        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty())
            return "{}";
        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        if (message == null)
            return "{}";
        Object content = message.get("content");
        return content != null ? content.toString() : "{}";
    }

    /**
     * Elimina los marcadores de bloque de código Markdown que los LLMs a veces
     * añaden.
     */
    private String stripMarkdownJson(String text) {
        if (text == null)
            return "{}";
        text = text.strip();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.strip();
    }

    /** Elimina marcadores de bloque de código genéricos (``` o ```dockerfile). */
    private String stripMarkdownCode(String text) {
        if (text == null)
            return "";
        text = text.strip();
        for (String lang : List.of("```dockerfile", "```bash", "```yaml", "```json", "```")) {
            if (text.startsWith(lang)) {
                text = text.substring(lang.length());
                break;
            }
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.strip();
    }

    /**
     * Realiza una llamada genérica a Groq y devuelve el texto crudo de la
     * respuesta.
     */
    private String callGroqRaw(String userPrompt, String systemMessage) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemMessage),
                Map.of("role", "user", "content", userPrompt));

        for (String candidateModel : resolveCandidateModels()) {
            try {
                Map<?, ?> response = callGroqChatCompletion(candidateModel, messages, 0.4, 2048);
                return extractMessageContent(response);
            } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
                String detail = e.getResponseBodyAsString();
                if (isModelDecommissioned(detail)) {
                    log.warn("Modelo Groq descontinuado ({}). Probando fallback.", candidateModel);
                    continue;
                }
                log.error("Error en llamada genérica a Groq (modelo {}): {}", candidateModel, detail);
                return "";
            } catch (Exception e) {
                log.error("Error en llamada genérica a Groq (modelo {}): {}", candidateModel, e.getMessage());
                return "";
            }
        }

        return "";
    }

    private Map<?, ?> callGroqChatCompletion(String modelName,
            List<Map<String, String>> messages,
            double temperature,
            int maxTokens) {
        var requestBody = Map.of(
                "model", modelName,
                "messages", messages,
                "temperature", temperature,
                "max_tokens", maxTokens);

        return groqClient.post()
                .uri("/openai/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private List<String> resolveCandidateModels() {
        Set<String> candidates = new LinkedHashSet<>();
        if (model != null && !model.isBlank()) {
            candidates.add(model.trim());
        }
        if (fallbackModels != null && !fallbackModels.isBlank()) {
            for (String entry : fallbackModels.split(",")) {
                String candidate = entry.trim();
                if (!candidate.isBlank()) {
                    candidates.add(candidate);
                }
            }
        }
        return new ArrayList<>(candidates);
    }

    private boolean isModelDecommissioned(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        return responseBody.contains("model_decommissioned")
                || responseBody.contains("decommissioned");
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(400);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Construye un archivo .env de ejemplo a partir de la lista de variables. */
    private String buildEnvExample(List<String> environment) {
        if (environment == null || environment.isEmpty()) {
            return "# No se detectaron variables de entorno específicas";
        }
        StringBuilder sb = new StringBuilder("# Variables de entorno requeridas\n");
        for (String var : environment) {
            sb.append(var).append("=\n");
        }
        return sb.toString().stripTrailing();
    }
}
