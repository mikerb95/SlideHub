package com.codebymike.slidehub.ui.controller;

import com.codebymike.slidehub.ui.model.Presentation;
import com.codebymike.slidehub.ui.model.Role;
import com.codebymike.slidehub.ui.model.User;
import com.codebymike.slidehub.ui.repository.PresentationRepository;
import com.codebymike.slidehub.ui.repository.UserRepository;
import com.codebymike.slidehub.ui.service.PresentationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private final PasswordEncoder passwordEncoder;

    @Value("${slidehub.mgr.create-dev-secret:}")
    private String createDevSecret;

    public MgrApiController(UserRepository userRepository,
                             PresentationRepository presentationRepository,
                             PresentationService presentationService,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.presentationRepository = presentationRepository;
        this.presentationService = presentationService;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Developer CRUD ────────────────────────────────────────────────────────

    @PostMapping("/developers")
    public ResponseEntity<Map<String, String>> createDeveloper(@RequestBody Map<String, String> body,
                                                                Authentication auth) {
        if (!validateDevSecret(body.get("secret"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Clave CREATE_DEV incorrecta."));
        }
        String username = body.get("username");
        String email    = body.get("email");
        String password = body.get("password");

        if (username == null || username.isBlank() || email == null || email.isBlank()
                || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username, email y password son obligatorios."));
        }
        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El username ya existe."));
        }
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "El email ya está registrado."));
        }

        User dev = new User();
        dev.setId(UUID.randomUUID().toString());
        dev.setUsername(username.trim());
        dev.setEmail(email.trim());
        dev.setPasswordHash(passwordEncoder.encode(password));
        dev.setRole(Role.DEVELOPER);
        dev.setEmailVerified(true);
        dev.setProfileCompleted(true);
        dev.setCreatedAt(LocalDateTime.now());
        userRepository.save(dev);

        log.info("[MGR] Developer '{}' creado por {}", username, auth.getName());
        return ResponseEntity.ok(Map.of("message", "Developer creado correctamente."));
    }

    @PutMapping("/developers/{id}")
    public ResponseEntity<Map<String, String>> updateDeveloper(@PathVariable String id,
                                                                @RequestBody Map<String, String> body,
                                                                Authentication auth) {
        if (!validateDevSecret(body.get("secret"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Clave CREATE_DEV incorrecta."));
        }
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User dev = opt.get();
        if (dev.getRole() != Role.DEVELOPER) {
            return ResponseEntity.badRequest().body(Map.of("error", "El usuario no es DEVELOPER."));
        }

        String username = body.get("username");
        String email    = body.get("email");
        String password = body.get("password");

        if (username != null && !username.isBlank()) {
            if (!username.equals(dev.getUsername()) && userRepository.existsByUsername(username)) {
                return ResponseEntity.badRequest().body(Map.of("error", "El username ya existe."));
            }
            dev.setUsername(username.trim());
        }
        if (email != null && !email.isBlank()) {
            if (!email.equals(dev.getEmail()) && userRepository.existsByEmail(email)) {
                return ResponseEntity.badRequest().body(Map.of("error", "El email ya está registrado."));
            }
            dev.setEmail(email.trim());
        }
        if (password != null && !password.isBlank()) {
            dev.setPasswordHash(passwordEncoder.encode(password));
        }
        userRepository.save(dev);

        log.info("[MGR] Developer '{}' editado por {}", dev.getUsername(), auth.getName());
        return ResponseEntity.ok(Map.of("message", "Developer actualizado correctamente."));
    }

    @DeleteMapping("/developers/{id}")
    public ResponseEntity<Map<String, String>> deleteDeveloper(@PathVariable String id,
                                                                @RequestParam String secret,
                                                                Authentication auth) {
        if (!validateDevSecret(secret)) {
            return ResponseEntity.status(403).body(Map.of("error", "Clave CREATE_DEV incorrecta."));
        }
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User dev = opt.get();
        if (dev.getRole() != Role.DEVELOPER) {
            return ResponseEntity.badRequest().body(Map.of("error", "El usuario no es DEVELOPER."));
        }
        if (dev.getUsername().equals(auth.getName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "No puedes eliminarte a ti mismo."));
        }

        userRepository.deleteById(id);
        log.info("[MGR] Developer '{}' eliminado por {}", dev.getUsername(), auth.getName());
        return ResponseEntity.ok(Map.of("message", "Developer eliminado."));
    }

    private boolean validateDevSecret(String provided) {
        if (createDevSecret == null || createDevSecret.isBlank()) return false;
        return createDevSecret.equals(provided);
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
