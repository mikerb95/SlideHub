package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ui.model.Presentation;
import com.brixo.slidehub.ui.model.Role;
import com.brixo.slidehub.ui.model.SourceType;
import com.brixo.slidehub.ui.model.User;
import com.brixo.slidehub.ui.repository.PresentationRepository;
import com.brixo.slidehub.ui.repository.PresentationSessionRepository;
import com.brixo.slidehub.ui.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrega métricas y estado de servicios para el panel /mgr.
 */
@Service
public class MgrService {

    private static final Logger log = LoggerFactory.getLogger(MgrService.class);

    private final UserRepository userRepository;
    private final PresentationRepository presentationRepository;
    private final PresentationSessionRepository sessionRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final DataSource dataSource;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket:}")
    private String s3Bucket;

    public MgrService(UserRepository userRepository,
                      PresentationRepository presentationRepository,
                      PresentationSessionRepository sessionRepository,
                      RedisTemplate<String, String> redisTemplate,
                      MongoTemplate mongoTemplate,
                      DataSource dataSource,
                      S3Client s3Client) {
        this.userRepository = userRepository;
        this.presentationRepository = presentationRepository;
        this.sessionRepository = sessionRepository;
        this.redisTemplate = redisTemplate;
        this.mongoTemplate = mongoTemplate;
        this.dataSource = dataSource;
        this.s3Client = s3Client;
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getUserStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", userRepository.count());
        stats.put("presenters", userRepository.countByRole(Role.PRESENTER));
        stats.put("admins", userRepository.countByRole(Role.ADMIN));
        stats.put("developers", userRepository.countByRole(Role.DEVELOPER));
        stats.put("local", userRepository.countLocalUsers());
        stats.put("github", userRepository.countGithubUsers());
        stats.put("google", userRepository.countGoogleUsers());
        return stats;
    }

    public Map<String, Object> getPresentationStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", presentationRepository.count());
        stats.put("drive", presentationRepository.countBySourceType(SourceType.DRIVE));
        stats.put("upload", presentationRepository.countBySourceType(SourceType.UPLOAD));
        stats.put("pptx", presentationRepository.countBySourceType(SourceType.PPTX));
        return stats;
    }

    public long getActiveSessionCount() {
        return sessionRepository.findAll().stream().filter(s -> s.isActive()).count();
    }

    public List<User> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Presentation> getAllPresentations() {
        return presentationRepository.findAllByOrderByCreatedAtDesc();
    }

    // ── Service Health ────────────────────────────────────────────────────────

    public Map<String, Object> getServicesHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("postgres", checkPostgres());
        health.put("redis", checkRedis());
        health.put("mongodb", checkMongoDB());
        health.put("s3", checkS3());
        return health;
    }

    private boolean checkPostgres() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            log.warn("PostgreSQL health check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkRedis() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkMongoDB() {
        try {
            mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
            return true;
        } catch (Exception e) {
            log.warn("MongoDB health check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkS3() {
        if (s3Bucket == null || s3Bucket.isBlank()) return false;
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(s3Bucket).build());
            return true;
        } catch (Exception e) {
            log.warn("S3 health check failed: {}", e.getMessage());
            return false;
        }
    }
}
