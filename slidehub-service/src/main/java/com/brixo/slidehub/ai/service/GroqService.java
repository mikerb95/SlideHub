package com.brixo.slidehub.ai.service;

import com.brixo.slidehub.ai.model.NoteContent;
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
import java.util.List;
import java.util.Map;

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

        var requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system",
                                "content",
                                "Eres un asistente que genera notas de presentación en español. "
                                        + "Responde SIEMPRE en JSON válido, sin markdown ni texto adicional."),
                        Map.of("role", "user", "content", prompt)),
                "temperature", 0.7,
                "max_tokens", 1024);

        try {
            Map<?, ?> response = groqClient.post()
                    .uri("/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String rawJson = extractMessageContent(response);
            rawJson = stripMarkdownJson(rawJson);
            log.debug("Groq generateNote respondió (slide {}): {} chars", slideNumber, rawJson.length());

            return objectMapper.readValue(rawJson, NoteContent.class);

        } catch (Exception e) {
            log.error("Error generando nota con Groq (slide {}): {}", slideNumber, e.getMessage());
            // Devuelve nota de fallback en lugar de propagar la excepción
            return new NoteContent(
                    "Slide " + slideNumber,
                    List.of("No se pudo generar la nota con IA. " + e.getMessage()),
                    "~2 min",
                    List.of(),
                    List.of());
        }
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
        var requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemMessage),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.4,
                "max_tokens", 2048);

        try {
            Map<?, ?> response = groqClient.post()
                    .uri("/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return extractMessageContent(response);
        } catch (Exception e) {
            log.error("Error en llamada genérica a Groq: {}", e.getMessage());
            return "";
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
