package com.brixo.slidehub.ui.controller;

import com.brixo.slidehub.ui.exception.UserAlreadyExistsException;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.UserRepository;
import com.brixo.slidehub.ui.service.AccountDeletionService;
import com.brixo.slidehub.ui.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;

/**
 * Controller de autenticación (HU-001, HU-002, HU-003).
 *
 * El POST de login local lo procesa Spring Security directamente.
 * Este controller gestiona: vistas GET, POST /register, verificación de email,
 * perfil de cuenta y vinculación de proveedores OAuth2.
 */
@Controller
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountDeletionService accountDeletionService;

    public AuthController(UserService userService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccountDeletionService accountDeletionService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountDeletionService = accountDeletionService;
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Muestra el formulario de login.
     * Si la sesión ya está activa, redirige a /presenter (HU-001 §3).
     */
    @GetMapping("/login")
    public String loginPage(Authentication authentication,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String accountDeleted,
            Model model) {
        if (isAuthenticated(authentication)) {
            return "redirect:/presenter";
        }
        if (error != null) {
            // Mensaje genérico — sin indicar qué campo falló (HU-001 §2)
            model.addAttribute("errorMessage", "Credenciales incorrectas. Inténtalo de nuevo.");
        }
        if ("oauth2".equals(error)) {
            model.addAttribute("errorMessage", "Error al iniciar sesión con el proveedor externo.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "Sesión cerrada correctamente.");
        }
        if (accountDeleted != null) {
            model.addAttribute("logoutMessage", "Cuenta eliminada correctamente.");
        }
        return "auth/login";
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    /**
     * Muestra el formulario de registro.
     */
    @GetMapping("/register")
    public String registerPage(Authentication authentication) {
        if (isAuthenticated(authentication)) {
            return "redirect:/presenter";
        }
        return "auth/register";
    }

    /**
     * Procesa el registro de una nueva cuenta local (HU-002).
     * Crea el usuario, lo persiste en PostgreSQL y envía email de verificación.
     */
    @PostMapping("/register")
    public String register(@RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {
        if (!password.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Las contraseñas no coinciden.");
            return "auth/register";
        }
        if (password.length() < 8) {
            model.addAttribute("errorMessage", "La contraseña debe tener al menos 8 caracteres.");
            return "auth/register";
        }
        try {
            userService.registerUser(username, email, password);
            model.addAttribute("successMessage",
                    "¡Cuenta creada! Revisa tu email (" + email
                            + ") para confirmar tu cuenta antes de iniciar sesión.");
            return "auth/register";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";
        } catch (UserAlreadyExistsException ex) {
            // Mensaje genérico para no revelar si fue el username o el email (seguridad)
            model.addAttribute("errorMessage", "El usuario o email ya está registrado. Prueba con otro.");
            return "auth/register";
        }
    }

    // ── Verificación de email ─────────────────────────────────────────────────

    /**
     * Confirma el email del usuario usando el token enviado por email.
     */
    @GetMapping("/verify")
    public String verifyEmail(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            model.addAttribute("errorMessage", "Token de verificación no proporcionado.");
            return "auth/login";
        }
        Optional<User> verified = userService.verifyEmail(token);
        if (verified.isPresent()) {
            model.addAttribute("successMessage", "¡Email confirmado! Ya puedes iniciar sesión.");
        } else {
            model.addAttribute("errorMessage", "El enlace de verificación es inválido o ya fue usado.");
        }
        return "auth/login";
    }

    // ── Completar perfil (usuarios OAuth2 nuevos) ─────────────────────────────

    /**
     * Muestra el formulario para completar el perfil tras el primer login OAuth2.
     * Pre-llena username y email desde los datos del proveedor.
     */
    @GetMapping("/complete-profile")
    public String completeProfilePage(Authentication authentication, Model model) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/auth/login";
        }
        User user = findCurrentUser(authentication);
        if (user == null || user.isProfileCompleted()) {
            return "redirect:/presentations";
        }
        model.addAttribute("user", user);
        return "auth/complete-profile";
    }

    /**
     * Procesa el formulario de completar perfil: actualiza username y email,
     * marca profileCompleted = true y redirige a /presentations.
     */
    @PostMapping("/complete-profile")
    public String completeProfile(Authentication authentication,
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/auth/login";
        }
        User user = findCurrentUser(authentication);
        if (user == null) {
            return "redirect:/auth/login";
        }
        if (user.isProfileCompleted()) {
            return "redirect:/presentations";
        }

        // Validar username
        if (username == null || username.isBlank() || username.length() < 3 || username.length() > 50) {
            model.addAttribute("user", user);
            model.addAttribute("errorMessage", "El username debe tener entre 3 y 50 caracteres.");
            return "auth/complete-profile";
        }

        // Validar email
        if (email == null || email.isBlank() || !email.contains("@")) {
            model.addAttribute("user", user);
            model.addAttribute("errorMessage", "Ingresa un email válido.");
            return "auth/complete-profile";
        }

        // Validar contraseña requerida para cuentas OAuth2
        if (password == null || password.length() < 8) {
            model.addAttribute("user", user);
            model.addAttribute("errorMessage", "La contraseña debe tener al menos 8 caracteres.");
            return "auth/complete-profile";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("user", user);
            model.addAttribute("errorMessage", "Las contraseñas no coinciden.");
            return "auth/complete-profile";
        }

        // Verificar unicidad del username (si cambió)
        if (!username.equals(user.getUsername())) {
            Optional<User> existing = userRepository.findByUsername(username);
            if (existing.isPresent()) {
                model.addAttribute("user", user);
                model.addAttribute("errorMessage", "Ese username ya está en uso. Elige otro.");
                return "auth/complete-profile";
            }
        }

        // Verificar unicidad del email (si cambió)
        if (!email.equals(user.getEmail())) {
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent()) {
                model.addAttribute("user", user);
                model.addAttribute("errorMessage", "Ese email ya está registrado.");
                return "auth/complete-profile";
            }
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setProfileCompleted(true);
        userRepository.save(user);

        return "redirect:/presentations";
    }

    // ── Vinculación de proveedores OAuth ─────────────────────────────────────

    /**
     * Inicia el flujo OAuth2 para vincular un proveedor al usuario autenticado.
     * Guarda un flag en sesión para redirigir al perfil tras completar.
     */
    @GetMapping("/link/{provider}")
    public String linkProvider(@PathVariable String provider, HttpSession session) {
        session.setAttribute("oauth2_link_return", "/auth/profile");
        return "redirect:/oauth2/authorization/" + provider;
    }

    // ── Perfil / vinculación OAuth ────────────────────────────────────────────

    /**
     * Muestra el perfil del usuario autenticado con los proveedores vinculados
     * (PLAN-EXPANSION.md Fase 1, tarea 12).
     */
    @GetMapping("/profile")
    public String profilePage(Authentication authentication, Model model) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/auth/login";
        }

        String username = resolveUsername(authentication);
        userRepository.findByUsername(username).ifPresent(user -> {
            model.addAttribute("user", user);
            model.addAttribute("githubLinked", user.getGithubId() != null);
            model.addAttribute("googleLinked", user.getGoogleId() != null);
        });

        return "auth/profile";
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private boolean isAuthenticated(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
    }

    /**
     * Resuelve el User de BD a partir de la autenticación actual.
     * Soporta OAuth2 (GitHub/Google) y login local.
     */
    private User findCurrentUser(Authentication authentication) {
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.oidc.user.OidcUser oidc) {
            String googleId = oidc.getSubject();
            if (googleId != null) {
                return userRepository.findByGoogleId(googleId).orElse(null);
            }
        } else if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            Object githubId = oAuth2User.getAttribute("id");
            if (githubId != null) {
                return userRepository.findByGithubId(githubId.toString()).orElse(null);
            }
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    private String resolveUsername(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            Object login = oAuth2User.getAttribute("login"); // GitHub
            Object email = oAuth2User.getAttribute("email"); // Google
            if (login != null)
                return login.toString();
            if (email != null)
                return email.toString();
        }
        return authentication.getName();
    }

}

    @PostMapping("/delete-account")
    public String deleteAccount(Authentication authentication,
            @RequestParam(name = "confirmation", required = false) String confirmation,
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            Model model) {
        if (!isAuthenticated(authentication)) {
            return "redirect:/auth/login";
        }

        if (confirmation == null || !"ELIMINAR".equals(confirmation.trim())) {
            User current = findCurrentUser(authentication);
            if (current != null) {
                model.addAttribute("user", current);
                model.addAttribute("githubLinked", current.getGithubId() != null);
                model.addAttribute("googleLinked", current.getGoogleId() != null);
            }
            model.addAttribute("deleteErrorMessage", "Para confirmar, escribe exactamente ELIMINAR.");
            return "auth/profile";
        }

        User current = findCurrentUser(authentication);
        if (current == null) {
            return "redirect:/auth/login";
        }

        try {
            accountDeletionService.deleteAccount(current.getId());
            session.invalidate();
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            return "redirect:/auth/login?accountDeleted=true";
        } catch (Exception ex) {
            log.error("Error eliminando cuenta para userId={}: {}", current.getId(), ex.getMessage(), ex);
            model.addAttribute("user", current);
            model.addAttribute("githubLinked", current.getGithubId() != null);
            model.addAttribute("googleLinked", current.getGoogleId() != null);
            model.addAttribute("deleteErrorMessage", "No se pudo eliminar la cuenta. Inténtalo nuevamente.");
            return "auth/profile";
        }
    }
