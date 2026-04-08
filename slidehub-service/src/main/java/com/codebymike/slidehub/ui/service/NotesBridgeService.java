package com.codebymike.slidehub.ui.service;

import com.codebymike.slidehub.ai.model.GenerateAllRequest;
import com.codebymike.slidehub.ai.model.PresenterNote;
import com.codebymike.slidehub.ai.model.RepoAnalysis;
import com.codebymike.slidehub.ai.model.SlideReference;
import com.codebymike.slidehub.ai.repository.PresenterNoteRepository;
import com.codebymike.slidehub.ai.service.NotesService;
import com.codebymike.slidehub.ai.service.RepoAnalysisService;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servicio de orquestación entre presentación e IA para generación de notas
 * (PLAN-EXPANSION.md Fase 3).
 *
 * Construye la solicitud completa a partir de datos de presentación
 * (slides + S3 URLs) y ejecuta la lógica de IA en-process.
 */
@Service
public class NotesBridgeService {

    private static final Logger log = LoggerFactory.getLogger(NotesBridgeService.class);

    private final NotesService notesService;
    private final RepoAnalysisService repoAnalysisService;
    private final PresenterNoteRepository presenterNoteRepository;
    private final ObjectMapper objectMapper;
    private static final Set<String> ALLOWED_CONTEXT_EXTENSIONS = Set.of(
            "md", "markdown", "txt", "xml", "yml", "yaml", "properties", "json", "dockerfile", "gradle", "java");
    private static final long MAX_CONTEXT_BYTES_PER_FILE = 512_000; // 500 KB
    private static final int MAX_CONTEXT_CHARS_TOTAL = 18_000;

    public NotesBridgeService(NotesService notesService,
            RepoAnalysisService repoAnalysisService,
            PresenterNoteRepository presenterNoteRepository,
            ObjectMapper objectMapper) {
        this.notesService = notesService;
        this.repoAnalysisService = repoAnalysisService;
        this.presenterNoteRepository = presenterNoteRepository;
        this.objectMapper = objectMapper;
    }

    // ── Generación de notas ───────────────────────────────────────────────────

    /**
     * Genera notas para todos los slides de una
     * presentación.
     *
     * @param presentationId ID de la presentación
     * @param repoUrl        URL del repo GitHub (puede ser null)
     * @param slideRefs      lista de pares {slideNumber, imageUrl} de S3
     * @return número de notas generadas
     */
    public int generateAllNotes(String presentationId, String repoUrl,
            List<Map<String, Object>> slideRefs) {
        return generateAllNotes(presentationId, repoUrl, slideRefs, null);
    }

    /**
     * Variante de generación con archivos de contexto adicionales cargados por el
     * usuario.
     */
    public int generateAllNotes(String presentationId, String repoUrl,
            List<Map<String, Object>> slideRefs,
            MultipartFile[] contextFiles) {
        List<SlideReference> slides = slideRefs.stream()
                .map(this::toSlideReference)
                .toList();

        String extraContext = extractContextFromFiles(contextFiles);

        GenerateAllRequest request = new GenerateAllRequest(
                presentationId,
                repoUrl != null ? repoUrl : "",
                extraContext,
                slides);

        try {
            return notesService.generateAll(request);
        } catch (Exception e) {
            log.error("Error ejecutando generate-all en monolito para {}: {}",
                    presentationId, e.getMessage());
            throw new RuntimeException("Error al generar notas: " + e.getMessage(), e);
        }
    }

    private String extractContextFromFiles(MultipartFile[] contextFiles) {
        if (contextFiles == null || contextFiles.length == 0) {
            return "";
        }

        StringBuilder merged = new StringBuilder();
        for (MultipartFile file : contextFiles) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "archivo";
            String ext = extensionOf(filename);
            boolean allowed = ALLOWED_CONTEXT_EXTENSIONS.contains(ext)
                    || filename.equalsIgnoreCase("Dockerfile")
                    || filename.equalsIgnoreCase("README")
                    || filename.equalsIgnoreCase("README.md")
                    || filename.equalsIgnoreCase("pom.xml");

            if (!allowed) {
                log.info("Archivo de contexto omitido por extensión no soportada: {}", filename);
                continue;
            }

            try {
                byte[] bytes = file.getBytes();
                if (bytes.length > MAX_CONTEXT_BYTES_PER_FILE) {
                    log.info("Archivo de contexto truncado por tamaño: {}", filename);
                    byte[] truncated = new byte[(int) MAX_CONTEXT_BYTES_PER_FILE];
                    System.arraycopy(bytes, 0, truncated, 0, truncated.length);
                    bytes = truncated;
                }

                String content = new String(bytes, StandardCharsets.UTF_8).trim();
                if (content.isBlank()) {
                    continue;
                }

                merged.append("\n\n=== Archivo: ").append(filename).append(" ===\n");
                merged.append(content);

                if (merged.length() >= MAX_CONTEXT_CHARS_TOTAL) {
                    merged.setLength(MAX_CONTEXT_CHARS_TOTAL);
                    merged.append("\n\n[Contexto truncado por límite de tamaño]");
                    break;
                }
            } catch (Exception ex) {
                log.warn("No se pudo leer archivo de contexto {}: {}", filename, ex.getMessage());
            }
        }

        return merged.toString().trim();
    }

    private String extensionOf(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            return filename.toLowerCase();
        }
        return filename.substring(idx + 1).toLowerCase();
    }

    /**
     * Obtiene todas las notas generadas de una presentación.
     *
     * @param presentationId ID de la presentación
     * @return lista de notas (o lista vacía si no hay ninguna)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getNotes(String presentationId) {
        try {
            List<PresenterNote> notes = presenterNoteRepository
                    .findByPresentationIdOrderBySlideNumberAsc(presentationId);

            return notes.stream()
                    .map(note -> (Map<String, Object>) objectMapper.convertValue(note, Map.class))
                    .toList();
        } catch (Exception e) {
            log.error("Error obteniendo notas para {}: {}",
                    presentationId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Ejecuta un análisis técnico del repositorio.
     *
     * @param repoUrl URL del repositorio GitHub
     * @return mapa con los campos del análisis (language, framework, technologies,
     *         etc.)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyzeRepo(String repoUrl) {
        try {
            RepoAnalysis analysis = repoAnalysisService.analyze(repoUrl);
            return objectMapper.convertValue(analysis, Map.class);
        } catch (Exception e) {
            log.error("Error analizando repo {}: {}", repoUrl, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    private SlideReference toSlideReference(Map<String, Object> slideRef) {
        Object slideNumberRaw = slideRef.get("slideNumber");
        int slideNumber = slideNumberRaw instanceof Number
                ? ((Number) slideNumberRaw).intValue()
                : Integer.parseInt(String.valueOf(slideNumberRaw));
        Object imageUrlRaw = slideRef.get("imageUrl");
        String imageUrl = imageUrlRaw != null ? String.valueOf(imageUrlRaw) : null;
        return new SlideReference(slideNumber, imageUrl);
    }
}
