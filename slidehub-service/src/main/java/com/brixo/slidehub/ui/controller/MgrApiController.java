package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.model.Role;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.PresentationRepository;
import com.brixo.slidehub.ui.repository.UserRepository;
import com.brixo.slidehub.ui.service.PresentationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API REST del panel /mgr para operaciones de gestión.
 * Requiere rol DEVELOPER (garantizado por SecurityConfig).
 */
@RestController
@RequestMapping("/mgr/api")
public class MgrApiController {

    private static final Logger log = LoggerFactory.getLogger(MgrApiController.class);

    private final UserRepository userRepository;
    private final PresentationRepository presentationRepository;
    private final PresentationService presentationService;

    public MgrApiController(UserRepository userRepository,
                             PresentationRepository presentationRepository,
                             PresentationService presentationService) {
        this.userRepository = userRepository;
        this.presentationRepository = presentationRepository;
        this.presentationService = presentationService;
    }

    @DeleteMapping("/presentations/{id}")
    public ResponseEntity<Map<String, String>> deletePresentation(@PathVariable String id,
                                                                   Authentication auth) {
        Optional<Presentation> opt = presentationRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Presentation p = opt.get();
        // Reutiliza la lógica completa (S3 + cascade) pasando el userId del propietario
        boolean deleted = presentationService.deletePresentation(p.getUser().getId(), id);
        if (!deleted) {
            return ResponseEntity.internalServerError().body(Map.of("error", "No se pudo eliminar."));
        }
        log.info("[MGR] Presentación '{}' eliminada por {}", id, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Presentación eliminada."));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable String id,
                                                           Authentication auth) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User target = opt.get();
        if (target.getRole() == Role.DEVELOPER) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se puede eliminar un usuario DEVELOPER."));
        }

        // Eliminar todas las presentaciones del usuario (con cascade S3 + BD)
        List<Presentation> presentations = presentationRepository.findByUserIdOrderByCreatedAtDesc(id);
        for (Presentation p : presentations) {
            presentationService.deletePresentation(id, p.getId());
        }

        userRepository.deleteById(id);
        log.info("[MGR] Usuario '{}' ({}) eliminado por {}", target.getUsername(), id, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Usuario eliminado."));
    }
}
