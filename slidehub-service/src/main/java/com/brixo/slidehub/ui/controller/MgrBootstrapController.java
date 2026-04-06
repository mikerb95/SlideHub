package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.model.Role;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Bootstrap de un primer usuario DEVELOPER.
 * Solo funciona si no existe ningún DEVELOPER en la BD y el secret es correcto.
 *
 * POST /mgr/bootstrap?secret=X&username=Y&password=Z&email=E
 */
@RestController
@RequestMapping("/mgr/bootstrap")
public class MgrBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(MgrBootstrapController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${slidehub.mgr.bootstrap-secret:}")
    private String bootstrapSecret;

    public MgrBootstrapController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> bootstrap(
            @RequestParam String secret,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String email) {

        if (bootstrapSecret.isBlank() || !bootstrapSecret.equals(secret)) {
            return ResponseEntity.status(403).body(Map.of("error", "Secret inválido."));
        }

        if (userRepository.existsByRole(Role.DEVELOPER)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ya existe un usuario DEVELOPER."));
        }

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El username ya está en uso."));
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El email ya está en uso."));
        }

        User dev = new User();
        dev.setId(UUID.randomUUID().toString());
        dev.setUsername(username);
        dev.setEmail(email);
        dev.setPasswordHash(passwordEncoder.encode(password));
        dev.setRole(Role.DEVELOPER);
        dev.setEmailVerified(true);
        dev.setProfileCompleted(true);
        dev.setCreatedAt(LocalDateTime.now());

        userRepository.save(dev);
        log.info("Usuario DEVELOPER '{}' creado via bootstrap.", username);

        return ResponseEntity.ok(Map.of("message", "Usuario DEVELOPER creado correctamente."));
    }
}
