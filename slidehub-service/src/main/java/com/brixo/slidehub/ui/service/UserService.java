package com.codebymike.slidehub.ui.service;

import com.codebymike.slidehub.ui.exception.UserAlreadyExistsException;
import com.codebymike.slidehub.ui.model.Role;
import com.codebymike.slidehub.ui.model.User;
import com.codebymike.slidehub.ui.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Lógica de negocio de usuarios: registro, verificación de email (HU-001/002).
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${slidehub.base-url:http://localhost:8082}")
    private String baseUrl;

    public UserService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            RedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Registra un nuevo usuario local con BCrypt.
     * Envía email de verificación via Resend.
     *
     * @throws UserAlreadyExistsException si el username o email ya existe
     */
    @Transactional
    public User registerUser(String username, String email, String rawPassword) {
        if (username == null || !username.matches("^[a-zA-Z0-9._-]{3,30}$")) {
            throw new IllegalArgumentException(
                    "El nombre de usuario solo puede contener letras, numeros, puntos, guiones y guiones bajos (3-30 caracteres).");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UserAlreadyExistsException("El nombre de usuario ya está registrado.");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("El email ya está registrado.");
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.PRESENTER);
        user.setEmailVerified(false);
        user.setEmailVerificationToken(verificationToken);
        user.setCreatedAt(LocalDateTime.now());

        User saved = userRepository.save(user);
        log.info("Usuario registrado: {} ({})", username, email);

        sendVerificationEmail(email, verificationToken);

        return saved;
    }

    /**
     * Verifica el email del usuario usando el token de confirmación.
     * Devuelve el usuario si el token es válido; vacío si ya expiró o es
     * incorrecto.
     */
    @Transactional
    public Optional<User> verifyEmail(String token) {
        return userRepository.findByEmailVerificationToken(token)
                .map(user -> {
                    user.setEmailVerified(true);
                    user.setEmailVerificationToken(null);
                    log.info("Email verificado para usuario: {}", user.getUsername());
                    return userRepository.save(user);
                });
    }

    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set("pwreset:" + token, user.getId(), Duration.ofMinutes(15));
            sendPasswordResetEmail(user.getEmail(), token);
            log.info("Solicitud de reseteo de contraseña creada para: {}", email);
        });
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        String userId = redisTemplate.opsForValue().get("pwreset:" + token);
        if (userId == null) {
            return false;
        }

        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isPresent()) {
            User user = optUser.get();
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            redisTemplate.delete("pwreset:" + token);
            log.info("Contraseña actualizada para usuario: {}", user.getUsername());
            return true;
        }
        return false;
    }

    // ── Privados ──────────────────────────────────────────────────────────────

    private void sendPasswordResetEmail(String email, String token) {
        String resetUrl = baseUrl + "/auth/reset-password?token=" + token;
        String html = """
                <h2 style="font-family:sans-serif">Restablece tu contraseña en SlideHub</h2>
                <p style="font-family:sans-serif">
                    Hemos recibido una solicitud para cambiar tu contraseña. Haz clic en el enlace de abajo para establecer una nueva:
                </p>
                <a href="%s"
                   style="background:#1f6feb;color:white;padding:10px 20px;
                          text-decoration:none;border-radius:6px;font-family:sans-serif;display:inline-block;">
                    Restablecer contraseña
                </a>
                <p style="font-family:sans-serif;color:#8b949e;font-size:0.85rem;margin-top:1rem">
                    Este enlace expirará en 15 minutos. Si no solicitaste este cambio, puedes ignorar este mensaje.
                </p>
                """
                .formatted(resetUrl);

        emailService.send(email, "Restablece tu contraseña en SlideHub", html);
    }

    private void sendVerificationEmail(String email, String token) {
        String confirmUrl = baseUrl + "/auth/verify?token=" + token;
        String html = """
                <h2 style="font-family:sans-serif">Confirma tu cuenta en SlideHub</h2>
                <p style="font-family:sans-serif">
                    Haz clic en el enlace para activar tu cuenta:
                </p>
                <a href="%s"
                   style="background:#1f6feb;color:white;padding:10px 20px;
                          text-decoration:none;border-radius:6px;font-family:sans-serif">
                    Confirmar cuenta
                </a>
                <p style="font-family:sans-serif;color:#8b949e;font-size:0.85rem;margin-top:1rem">
                    Si no creaste esta cuenta, puedes ignorar este mensaje.
                </p>
                """.formatted(confirmUrl);

        emailService.send(email, "Confirma tu cuenta en SlideHub", html);
    }
}
