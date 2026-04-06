package com.brixo.slidehub.ui.repository;

import com.brixo.slidehub.ui.model.Role;
import com.brixo.slidehub.ui.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA de usuarios.
 */
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByGithubId(String githubId);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmailVerificationToken(String token);

    long countByRole(Role role);

    boolean existsByRole(Role role);

    List<User> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(u) FROM User u WHERE u.githubId IS NOT NULL")
    long countGithubUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.googleId IS NOT NULL")
    long countGoogleUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.passwordHash IS NOT NULL AND u.githubId IS NULL AND u.googleId IS NULL")
    long countLocalUsers();
}
