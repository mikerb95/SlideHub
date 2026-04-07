package com.codebymike.slidehub.ui.controller;

import com.codebymike.slidehub.ui.model.QuestionStatus;
import com.codebymike.slidehub.ui.model.User;
import com.codebymike.slidehub.ui.repository.UserRepository;
import com.codebymike.slidehub.ui.service.QuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints de Q&A para la funcionalidad de preguntas del stream.
 *
 * Endpoints públicos (viewer):
 *   POST /submit         — enviar pregunta (requiere viewerToken)
 *   POST /{id}/upvote    — votar pregunta (requiere viewerToken)
 *   GET  /settings       — leer config de Q&A de la presentación
 *
 * Endpoints protegidos (PRESENTER/ADMIN):
 *   GET  /               — listar todas las preguntas de la sesión activa
 *   PUT  /{id}/status    — marcar como ANSWERED o DISMISSED
 */
@RestController
@RequestMapping("/api/presentations/{presentationId}/questions")
public class QuestionController {

    private final QuestionService questionService;
    private final UserRepository userRepository;

    public QuestionController(QuestionService questionService, UserRepository userRepository) {
        this.questionService = questionService;
        this.userRepository = userRepository;
    }

    record SubmitRequest(String viewerToken, String displayName, boolean anonymous, String content) {}
    record UpvoteRequest(String viewerToken) {}
    record StatusRequest(String status) {}

    /** Viewer: envía una pregunta a la sesión activa. */
    @PostMapping("/submit")
    public ResponseEntity<?> submit(@PathVariable String presentationId,
            @RequestBody SubmitRequest request) {
        try {
            QuestionService.SubmitResult result = questionService.submit(
                    presentationId,
                    request.viewerToken(),
                    request.displayName(),
                    request.anonymous(),
                    request.content());
            return ResponseEntity.ok(Map.of("questionId", result.questionId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /** Viewer: vota una pregunta. */
    @PostMapping("/{questionId}/upvote")
    public ResponseEntity<?> upvote(@PathVariable String presentationId,
            @PathVariable String questionId,
            @RequestBody UpvoteRequest request) {
        try {
            questionService.upvote(questionId, presentationId, request.viewerToken());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /** Viewer: consulta settings de Q&A de la presentación (para configurar el widget). */
    @GetMapping("/settings")
    public ResponseEntity<?> settings(@PathVariable String presentationId) {
        return ResponseEntity.ok(questionService.getSettings(presentationId));
    }

    /**
     * Presenter/Admin: lista preguntas de la sesión activa.
     * ?status=PENDING filtra solo las pendientes.
     */
    @GetMapping
    public ResponseEntity<?> list(@PathVariable String presentationId,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        resolveUser(authentication);
        if ("PENDING".equalsIgnoreCase(status)) {
            return ResponseEntity.ok(questionService.listPending(presentationId));
        }
        return ResponseEntity.ok(questionService.listForPresentation(presentationId));
    }

    /** Presenter/Admin: actualiza el estado de una pregunta. */
    @PutMapping("/{questionId}/status")
    public ResponseEntity<?> updateStatus(@PathVariable String presentationId,
            @PathVariable String questionId,
            @RequestBody StatusRequest request,
            Authentication authentication) {
        resolveUser(authentication);
        try {
            QuestionStatus newStatus = QuestionStatus.valueOf(request.status().toUpperCase());
            questionService.updateStatus(questionId, presentationId, newStatus);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private User resolveUser(Authentication auth) {
        if (auth == null) throw new IllegalStateException("No autenticado.");
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            return userRepository.findByGoogleId(oidc.getSubject())
                    .orElseThrow(() -> new IllegalStateException("Usuario no encontrado."));
        }
        if (auth.getPrincipal() instanceof OAuth2User oauth2) {
            Object id = oauth2.getAttribute("id");
            if (id != null) return userRepository.findByGithubId(id.toString())
                    .orElseThrow(() -> new IllegalStateException("Usuario no encontrado."));
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario no encontrado."));
    }
}
