package com.codebymike.slidehub.ui.service;

import com.codebymike.slidehub.ui.model.Role;
import com.codebymike.slidehub.ui.model.User;
import com.codebymike.slidehub.ui.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Procesa el login via GitHub (PLAN-EXPANSION.md Fase 1, tareas 9-12).
 *
 * Soporta dos flujos:
 * - Login: crea cuenta nueva o reutiliza existente (merge by email).
 * - Linking: si ya hay usuario autenticado, vincula GitHub a esa cuenta.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();

        try {
            User user = switch (registrationId) {
                case "github" -> processGithubUser(request, oauth2User);
                case "google-drive" -> processDriveUser(oauth2User);
                default -> throw new OAuth2AuthenticationException(
                        new OAuth2Error("unsupported_provider"),
                        "Proveedor OAuth2 no soportado: " + registrationId);
            };

            String nameAttribute = request.getClientRegistration()
                    .getProviderDetails()
                    .getUserInfoEndpoint()
                    .getUserNameAttributeName();

            return new DefaultOAuth2User(
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                    oauth2User.getAttributes(),
                    nameAttribute);
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error procesando usuario OAuth2 ({}): {}", registrationId, ex.getMessage(), ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("oauth2_processing_error"), ex.getMessage(), ex);
        }
    }

    // ── GitHub ────────────────────────────────────────────────────────────────

    private User processGithubUser(OAuth2UserRequest request, OAuth2User oauth2User) {
        String githubId = oauth2User.getAttribute("id").toString();
        String githubUsername = oauth2User.getAttribute("login");
        String email = oauth2User.getAttribute("email"); // puede ser null (email privado)
        String avatarUrl = oauth2User.getAttribute("avatar_url"); // foto de perfil
        String accessToken = request.getAccessToken().getTokenValue();

        // 1. ¿Existe usuario con este githubId?
        Optional<User> byGithubId = userRepository.findByGithubId(githubId);
        if (byGithubId.isPresent()) {
            User existing = byGithubId.get();
            existing.setGithubUsername(githubUsername);
            existing.setGithubAccessToken(accessToken);
            if (existing.getProfileImageUrl() == null && avatarUrl != null) {
                existing.setProfileImageUrl(avatarUrl);
            }
            log.debug("GitHub login: usuario existente vinculado ({})", existing.getUsername());
            return userRepository.save(existing);
        }

        // 2. Linking: si hay usuario autenticado, vincular GitHub a esa cuenta
        Optional<User> authenticated = findAuthenticatedUser();
        if (authenticated.isPresent()) {
            User existing = authenticated.get();
            existing.setGithubId(githubId);
            existing.setGithubUsername(githubUsername);
            existing.setGithubAccessToken(accessToken);
            if (existing.getProfileImageUrl() == null && avatarUrl != null) {
                existing.setProfileImageUrl(avatarUrl);
            }
            log.info("GitHub: vinculado a usuario autenticado ({})", existing.getUsername());
            return userRepository.save(existing);
        }

        // 3. ¿Existe usuario con este email?
        if (email != null) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                User existing = byEmail.get();
                existing.setGithubId(githubId);
                existing.setGithubUsername(githubUsername);
                existing.setGithubAccessToken(accessToken);
                if (existing.getProfileImageUrl() == null && avatarUrl != null) {
                    existing.setProfileImageUrl(avatarUrl);
                }
                log.info("GitHub login: vinculado a cuenta existente por email ({})", email);
                return userRepository.save(existing);
            }
        }

        // 4. Crear cuenta nueva
        String resolvedUsername = resolveUniqueUsername(githubUsername, "gh");
        String resolvedEmail = email != null ? email : resolvedUsername + "@github.oauth.placeholder";

        User newUser = new User();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setUsername(resolvedUsername);
        newUser.setEmail(resolvedEmail);
        newUser.setRole(Role.PRESENTER);
        newUser.setEmailVerified(email != null); // solo verificado si GitHub lo proveyó
        newUser.setGithubId(githubId);
        newUser.setGithubUsername(githubUsername);
        newUser.setGithubAccessToken(accessToken);
        newUser.setProfileImageUrl(avatarUrl);
        newUser.setProfileCompleted(false);
        newUser.setCreatedAt(LocalDateTime.now());
        log.info("GitHub login: nueva cuenta creada para {} ({})", resolvedUsername, resolvedEmail);
        return userRepository.save(newUser);
    }

    // ── Google Drive ──────────────────────────────────────────────────────────

    /**
     * Maneja la autorización de Google Drive (no es login: el usuario ya existe).
     * Encuentra el usuario por sesión activa o por email; no crea cuentas nuevas.
     */
    private User processDriveUser(OAuth2User oauth2User) {
        // Si hay sesión activa, reutilizar ese usuario
        Optional<User> authenticated = findAuthenticatedUser();
        if (authenticated.isPresent()) {
            log.debug("Drive auth: usando usuario autenticado ({})", authenticated.get().getUsername());
            return authenticated.get();
        }

        // Fallback: buscar por email del token de Drive
        String email = oauth2User.getAttribute("email");
        if (email != null) {
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                log.debug("Drive auth: usuario encontrado por email ({})", email);
                return byEmail.get();
            }
        }

        throw new OAuth2AuthenticationException(
                new OAuth2Error("user_not_found"),
                "Drive auth: no se encontró usuario para " + email);
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    /**
     * Busca al usuario actualmente autenticado en la base de datos.
     * Soporta sesiones iniciadas via form login, GitHub OAuth2 o Google OIDC.
     */
    private Optional<User> findAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof OAuth2User oauth2) {
            Object githubId = oauth2.getAttribute("id");
            if (githubId != null) {
                return userRepository.findByGithubId(githubId.toString());
            }
            Object googleId = oauth2.getAttribute("sub");
            if (googleId != null) {
                return userRepository.findByGoogleId(googleId.toString());
            }
        }
        return userRepository.findByUsername(auth.getName());
    }

    private String resolveUniqueUsername(String base, String prefix) {
        if (base == null || base.isBlank())
            base = prefix + "_user";
        String candidate = base;
        int attempt = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            candidate = base + "_" + prefix + attempt++;
        }
        return candidate;
    }
}
