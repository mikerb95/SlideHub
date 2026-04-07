package com.codebymike.slidehub.ui.service;

import com.codebymike.slidehub.ui.model.Presentation;
import com.codebymike.slidehub.ui.model.PresentationSession;
import com.codebymike.slidehub.ui.model.Question;
import com.codebymike.slidehub.ui.model.QuestionStatus;
import com.codebymike.slidehub.ui.repository.PresentationRepository;
import com.codebymike.slidehub.ui.repository.PresentationSessionRepository;
import com.codebymike.slidehub.ui.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final PresentationSessionRepository sessionRepository;
    private final PresentationRepository presentationRepository;
    private final ViewerService viewerService;

    public QuestionService(QuestionRepository questionRepository,
            PresentationSessionRepository sessionRepository,
            PresentationRepository presentationRepository,
            ViewerService viewerService) {
        this.questionRepository = questionRepository;
        this.sessionRepository = sessionRepository;
        this.presentationRepository = presentationRepository;
        this.viewerService = viewerService;
    }

    public record QuestionItem(
            String id,
            String displayName,
            boolean anonymous,
            String content,
            String status,
            int upvotes,
            LocalDateTime createdAt) {
    }

    public record SubmitResult(String questionId) {
    }

    /**
     * Envía una pregunta desde un viewer.
     *
     * Valida:
     * - Que haya una sesión activa para la presentación
     * - Que el viewer esté registrado (viewerToken conocido en Redis)
     * - Que la presentación tenga Q&A habilitado
     * - Que si anonymous=true, la presentación lo permita
     */
    @Transactional
    public SubmitResult submit(String presentationId,
            String viewerToken,
            String displayName,
            boolean anonymous,
            String content) {

        Presentation presentation = presentationRepository.findById(presentationId)
                .orElseThrow(() -> new IllegalArgumentException("Presentación no encontrada."));

        if (!presentation.isQuestionsEnabled()) {
            throw new IllegalArgumentException("Las preguntas están deshabilitadas para esta presentación.");
        }

        if (anonymous && !presentation.isAllowAnonymousQuestions()) {
            throw new IllegalArgumentException("Las preguntas anónimas no están permitidas en esta presentación.");
        }

        PresentationSession session = sessionRepository
                .findByPresentationIdAndActiveTrue(presentationId)
                .orElseThrow(() -> new IllegalArgumentException("No hay una sesión activa para esta presentación."));

        if (!viewerService.isRegistered(session.getId(), viewerToken)) {
            throw new IllegalArgumentException("viewerToken inválido o expirado.");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("El contenido de la pregunta es obligatorio.");
        }
        if (content.length() > 500) {
            throw new IllegalArgumentException("La pregunta no puede superar los 500 caracteres.");
        }

        Question question = new Question();
        question.setId(UUID.randomUUID().toString());
        question.setSession(session);
        question.setViewerToken(viewerToken);
        question.setAnonymous(anonymous);
        question.setDisplayName(anonymous ? null : trimName(displayName));
        question.setContent(content.trim());
        question.setStatus(QuestionStatus.PENDING);
        question.setUpvotes(0);
        question.setCreatedAt(LocalDateTime.now());

        questionRepository.save(question);
        return new SubmitResult(question.getId());
    }

    /** Lista todas las preguntas de la sesión activa de una presentación. */
    public List<QuestionItem> listForPresentation(String presentationId) {
        return sessionRepository.findByPresentationIdAndActiveTrue(presentationId)
                .map(session -> questionRepository
                        .findBySessionIdOrderByCreatedAtAsc(session.getId())
                        .stream()
                        .map(this::toItem)
                        .toList())
                .orElse(List.of());
    }

    /** Lista solo las preguntas PENDING de la sesión activa. */
    public List<QuestionItem> listPending(String presentationId) {
        return sessionRepository.findByPresentationIdAndActiveTrue(presentationId)
                .map(session -> questionRepository
                        .findBySessionIdAndStatusOrderByCreatedAtAsc(session.getId(), QuestionStatus.PENDING)
                        .stream()
                        .map(this::toItem)
                        .toList())
                .orElse(List.of());
    }

    /** Actualiza el estado de una pregunta (ANSWERED o DISMISSED). */
    @Transactional
    public void updateStatus(String questionId, String presentationId, QuestionStatus newStatus) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Pregunta no encontrada."));

        if (!question.getSession().getPresentation().getId().equals(presentationId)) {
            throw new IllegalArgumentException("Pregunta no pertenece a esta presentación.");
        }

        question.setStatus(newStatus);
        questionRepository.save(question);
    }

    /** Incrementa el contador de upvotes de una pregunta. */
    @Transactional
    public void upvote(String questionId, String presentationId, String viewerToken) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Pregunta no encontrada."));

        if (!question.getSession().getPresentation().getId().equals(presentationId)) {
            throw new IllegalArgumentException("Pregunta no pertenece a esta presentación.");
        }

        if (question.getStatus() != QuestionStatus.PENDING) {
            throw new IllegalArgumentException("Solo se pueden votar preguntas pendientes.");
        }

        String sessionId = question.getSession().getId();
        if (!viewerService.isRegistered(sessionId, viewerToken)) {
            throw new IllegalArgumentException("viewerToken inválido o expirado.");
        }

        questionRepository.incrementUpvotes(questionId);
    }

    /** Devuelve los settings de Q&A de una presentación para el viewer. */
    public Map<String, Object> getSettings(String presentationId) {
        return presentationRepository.findById(presentationId)
                .map(p -> Map.<String, Object>of(
                        "questionsEnabled", p.isQuestionsEnabled(),
                        "allowAnonymousQuestions", p.isAllowAnonymousQuestions()))
                .orElse(Map.of("questionsEnabled", false, "allowAnonymousQuestions", false));
    }

    private QuestionItem toItem(Question q) {
        return new QuestionItem(
                q.getId(),
                q.isAnonymous() ? null : q.getDisplayName(),
                q.isAnonymous(),
                q.getContent(),
                q.getStatus().name(),
                q.getUpvotes(),
                q.getCreatedAt());
    }

    private String trimName(String name) {
        if (name == null || name.isBlank()) return null;
        return name.trim().substring(0, Math.min(name.trim().length(), 100));
    }
}
