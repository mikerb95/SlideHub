package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ai.model.GenerateAllRequest;
import com.brixo.slidehub.ai.model.PresenterNote;
import com.brixo.slidehub.ai.model.RepoAnalysis;
import com.brixo.slidehub.ai.model.SlideReference;
import com.brixo.slidehub.ai.repository.PresenterNoteRepository;
import com.brixo.slidehub.ai.service.NotesService;
import com.brixo.slidehub.ai.service.RepoAnalysisService;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Servicio puente entre ui-service y ai-service para generación de notas
 * (PLAN-EXPANSION.md Fase 3).
 *
 * El ui-service tiene acceso a los datos de la presentación (slides + S3 URLs)
 * en
 * PostgreSQL. Este servicio construye la solicitud completa para ai-service,
 * incluyendo las URLs de las imágenes, y la envía vía WebClient.
 */
@Service
public class NotesBridgeService {

    private static final Logger log = LoggerFactory.getLogger(NotesBridgeService.class);

    private final NotesService notesService;
    private final RepoAnalysisService repoAnalysisService;
    private final PresenterNoteRepository presenterNoteRepository;
    private final ObjectMapper objectMapper;

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
     * Solicita al ai-service que genere notas para todos los slides de una
     * presentación.
     *
     * @param presentationId ID de la presentación
     * @param repoUrl        URL del repo GitHub (puede ser null)
     * @param slideRefs      lista de pares {slideNumber, imageUrl} de S3
     * @return número de notas generadas
     */
    public int generateAllNotes(String presentationId, String repoUrl,
            List<Map<String, Object>> slideRefs) {
        List<SlideReference> slides = slideRefs.stream()
            .map(this::toSlideReference)
            .toList();

        GenerateAllRequest request = new GenerateAllRequest(
            presentationId,
            repoUrl != null ? repoUrl : "",
            slides);

        try {
            return notesService.generateAll(request);
        } catch (Exception e) {
            log.error("Error ejecutando generate-all en monolito para {}: {}",
                    presentationId, e.getMessage());
            throw new RuntimeException("Error al generar notas: " + e.getMessage(), e);
        }
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
                .map(note -> objectMapper.convertValue(note, Map.class))
                .toList();
        } catch (Exception e) {
            log.error("Error obteniendo notas para {}: {}",
                    presentationId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Solicita al ai-service un análisis técnico del repositorio.
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
