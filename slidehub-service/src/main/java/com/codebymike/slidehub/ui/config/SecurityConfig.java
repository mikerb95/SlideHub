package com.codebymike.slidehub.ui.config;

import com.codebymike.slidehub.ui.model.User;
import com.codebymike.slidehub.ui.repository.UserRepository;
import com.codebymike.slidehub.ui.service.AuthenticatedSessionTracker;
import com.codebymike.slidehub.ui.service.CustomOAuth2UserService;
import com.codebymike.slidehub.ui.service.CustomOidcUserService;
import com.codebymike.slidehub.ui.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpSession;
import java.util.Optional;

/**
 * Configuración de seguridad de ui-service (CLAUDE.md §11).
 *
 * Fase 1: auth local (BCrypt + PostgreSQL) + OAuth2 (GitHub, Google).
 * Estrategia de autorización según AGENTS.md §2.2.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final CustomUserDetailsService userDetailsService;
        private final CustomOAuth2UserService oAuth2UserService;
        private final CustomOidcUserService oidcUserService;
        private final UserRepository userRepository;
        private final UserActivityTrackingFilter userActivityTrackingFilter;

        public SecurityConfig(CustomUserDetailsService userDetailsService,
                        CustomOAuth2UserService oAuth2UserService,
                        CustomOidcUserService oidcUserService,
                        UserRepository userRepository,
                        UserActivityTrackingFilter userActivityTrackingFilter) {
                this.userDetailsService = userDetailsService;
                this.oAuth2UserService = oAuth2UserService;
                this.oidcUserService = oidcUserService;
                this.userRepository = userRepository;
                this.userActivityTrackingFilter = userActivityTrackingFilter;
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                LoginUrlAuthenticationEntryPoint loginEntryPoint = new LoginUrlAuthenticationEntryPoint("/auth/login");
                loginEntryPoint.setFavorRelativeUris(true);

                http
                                .authenticationProvider(authenticationProvider())
                                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/mgr/bootstrap", "/mgr/api/**", "/api/webhooks/**"))
                                .authorizeHttpRequests(auth -> auth
                                                // Vistas públicas (HU-005, HU-011, HU-012, HU-013, Legal, Pricing)
                                                .requestMatchers("/slides", "/remote", "/demo", "/showcase", "/join",
                                                                "/privacidad", "/politicadeuso", "/copyright",
                                                                "/pricing", "/checkout", "/billing/process")
                                                .permitAll()
                                                // Join de reunión por QR + comandos en remoto (públicos pero
                                                // tokenizados)
                                                .requestMatchers(
                                                                "/api/presentations/*/meeting/join-options",
                                                                "/api/presentations/*/meeting/join",
                                                                "/api/presentations/*/meeting/assignment-check",
                                                                "/api/presentations/*/meeting/help",
                                                                "/api/presentations/*/meeting/assist/audio")
                                                .permitAll()
                                                // Stream viewers: join/heartbeat/leave/hand/stats + Q&A público
                                                .requestMatchers(
                                                                "/api/presentations/*/stream/**",
                                                                "/api/presentations/*/questions/submit",
                                                                "/api/presentations/*/questions/settings",
                                                                "/api/presentations/*/questions/*/upvote")
                                                .permitAll()
                                                .requestMatchers("/api/webhooks/**").permitAll()
                                                .requestMatchers("/api/presentations/*/slides").permitAll()
                                                .requestMatchers("/api/presentations/*/slides/*/image").permitAll()
                                                // Registro de dispositivos (heartbeat público; lectura ADMIN)
                                                .requestMatchers(HttpMethod.POST, "/api/devices/register",
                                                                "/api/devices/heartbeat")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/devices",
                                                                "/api/devices/token/*")
                                                .hasRole("DEVELOPER")
                                                // Auth pública — incluye rutas OAuth2 de Spring Security
                                                .requestMatchers("/auth/**", "/oauth2/**", "/login/oauth2/**")
                                                .permitAll()
                                                // Assets estáticos de slides
                                                .requestMatchers("/presentation/**").permitAll()
                                                // Importación y gestión de presentaciones — requiere HOST o PRESENTER
                                                .requestMatchers("/presentations/**").hasAnyRole("HOST", "PRESENTER")
                                                .requestMatchers("/api/presentations/**")
                                                .hasAnyRole("HOST", "PRESENTER")
                                                // Polling de dispositivos (pasa por gateway, llega como /api/**)
                                                .requestMatchers("/api/**").permitAll()
                                                // Recursos estáticos (CSS, JS, imágenes)
                                                .requestMatchers("/css/**", "/js/**", "/images/**", "/slides/**")
                                                .permitAll()
                                                // Documentación/calidad y checks de prueba públicos
                                                .requestMatchers("/calidad", "/status", "/status/api/checks",
                                                                "/actuator/health", "/actuator/info",
                                                                "/sustentacion", "/presentacion", "/metodologia",
                                                                "/matriz-pdca", "/lista",
                                                                "/ai-guide",
                                                                "/uptime", "/api/uptime/status")
                                                .permitAll()
                                                // Bootstrap del primer DEVELOPER — público pero protegido por secret
                                                .requestMatchers("/mgr/bootstrap", "/error").permitAll()
                                                // Panel de gestión — solo DEVELOPER
                                                .requestMatchers("/mgr", "/mgr/**")
                                                .hasRole("DEVELOPER")
                                                // Panel del presentador y main panel — requiere HOST o PRESENTER
                                                .requestMatchers("/presenter", "/main-panel")
                                                .hasAnyRole("HOST", "PRESENTER")
                                                // Perfil del usuario — requiere estar autenticado
                                                .requestMatchers("/auth/profile").authenticated()
                                                .anyRequest().authenticated())
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(loginEntryPoint))
                                // Login local con formulario
                                .formLogin(form -> form
                                                .loginPage("/auth/login")
                                                .loginProcessingUrl("/auth/login")
                                                .successHandler(formLoginSuccessHandler())
                                                .failureUrl("/auth/login?error=true")
                                                .permitAll())
                                // Login OAuth2 (GitHub y Google)
                                .oauth2Login(oauth -> oauth
                                                .loginPage("/auth/login")
                                                .successHandler(oauth2SuccessHandler())
                                                .failureUrl("/auth/login?error=oauth2")
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(oAuth2UserService)
                                                                .oidcUserService(oidcUserService)))
                                .logout(logout -> logout
                                                .logoutUrl("/auth/logout")
                                                .logoutSuccessUrl("/auth/login?logout=true")
                                                .invalidateHttpSession(true))
                                .addFilterAfter(userActivityTrackingFilter, UsernamePasswordAuthenticationFilter.class);
                return http.build();
        }

        /**
         * Provider que conecta Spring Security con CustomUserDetailsService + BCrypt.
         * Inyección explícita para evitar la auto-configuración de Spring Security
         * que podría interferir con la coexistencia OAuth2 + form login.
         *
         * Spring Security 6.x: DaoAuthenticationProvider requiere UserDetailsService en
         * constructor.
         */
        @Bean
        public DaoAuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder());
                return provider;
        }

        /**
         * Success handler para OAuth2: si el flujo viene de /auth/link/{provider},
         * redirige al perfil; si no, verifica si el usuario completó su perfil.
         * Usuarios nuevos (profileCompleted=false) van a /auth/complete-profile.
         */
        @Bean
        public AuthenticationSuccessHandler formLoginSuccessHandler() {
                return (request, response, authentication) -> {
                        String ipAddress = request.getRemoteAddr();
                        resolveUserFromAuth(authentication).ifPresent(user -> {
                                user.setLastLoginIp(ipAddress);
                                userRepository.save(user);
                        });

                        boolean isDeveloper = authentication.getAuthorities().stream()
                                        .anyMatch(a -> a.getAuthority().equals("ROLE_DEVELOPER"));
                        response.sendRedirect(isDeveloper ? "/mgr" : "/presentations");
                };
        }

        @Bean
        public AuthenticationSuccessHandler oauth2SuccessHandler() {
                return (request, response, authentication) -> {
                        String ipAddress = request.getRemoteAddr();
                        resolveUserFromAuth(authentication).ifPresent(user -> {
                                if (user.getRegistrationIp() == null) {
                                        user.setRegistrationIp(ipAddress);
                                }
                                user.setLastLoginIp(ipAddress);
                                userRepository.save(user);
                        });

                        // Drive auth: redirige al wizard, no hace login normal
                        if (authentication instanceof OAuth2AuthenticationToken oauthToken
                                        && "google-drive".equals(oauthToken.getAuthorizedClientRegistrationId())) {
                                response.sendRedirect("/presentations/wizard?tab=drive");
                                return;
                        }

                        HttpSession session = request.getSession(false);
                        String returnUrl = null;
                        if (session != null) {
                                returnUrl = (String) session.getAttribute("oauth2_link_return");
                                session.removeAttribute("oauth2_link_return");
                        }
                        if (returnUrl != null) {
                                response.sendRedirect(returnUrl);
                                return;
                        }

                        // Verificar si el usuario completó su perfil
                        Optional<User> user = resolveUserFromAuth(authentication);
                        if (user.isPresent() && !user.get().isProfileCompleted()) {
                                response.sendRedirect("/auth/complete-profile");
                                return;
                        }
                        response.sendRedirect("/presentations");
                };
        }

        private Optional<User> resolveUserFromAuth(org.springframework.security.core.Authentication auth) {
                if (auth.getPrincipal() instanceof OidcUser oidc) {
                        String googleId = oidc.getSubject();
                        if (googleId != null) {
                                return userRepository.findByGoogleId(googleId);
                        }
                } else if (auth.getPrincipal() instanceof OAuth2User oauth2) {
                        Object githubId = oauth2.getAttribute("id");
                        if (githubId != null) {
                                return userRepository.findByGithubId(githubId.toString());
                        }
                }
                return userRepository.findByUsername(auth.getName());
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public ServletListenerRegistrationBean<AuthenticatedSessionTracker> authenticatedSessionTrackerListener(
                        AuthenticatedSessionTracker authenticatedSessionTracker) {
                return new ServletListenerRegistrationBean<>(authenticatedSessionTracker);
        }
}
