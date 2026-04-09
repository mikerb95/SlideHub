package com.codebymike.slidehub.ui.service;

import com.codebymike.slidehub.ui.model.DbColumnMeta;
import com.codebymike.slidehub.ui.model.DbTableMeta;
import com.codebymike.slidehub.ui.model.Presentation;
import com.codebymike.slidehub.ui.model.Role;
import com.codebymike.slidehub.ui.model.SourceType;
import com.codebymike.slidehub.ui.model.User;
import com.codebymike.slidehub.ui.repository.PresentationRepository;
import com.codebymike.slidehub.ui.repository.PresentationSessionRepository;
import com.codebymike.slidehub.ui.repository.UserRepository;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
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
        stats.put("hosts", userRepository.countByRole(Role.HOST));
        stats.put("presenters", userRepository.countByRole(Role.PRESENTER));
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

    // ── DB Metadata ───────────────────────────────────────────────────────────

    /**
     * Lista todas las tablas del schema público con su nº de filas.
     */
    public List<DbTableMeta> getAllTables() {
        List<DbTableMeta> tables = new ArrayList<>();
        String sql = """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """;
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("table_name");
                long count = queryRowCount(conn, name);
                tables.add(new DbTableMeta(name, count, List.of()));
            }
        } catch (Exception e) {
            log.warn("Error listando tablas: {}", e.getMessage());
        }
        return tables;
    }

    /**
     * Devuelve columnas y row count de una tabla específica.
     */
    public DbTableMeta getTableDetail(String tableName) {
        String colSql = """
                SELECT c.column_name, c.data_type, c.is_nullable, c.column_default,
                       c.character_maximum_length, c.numeric_precision
                FROM information_schema.columns c
                WHERE c.table_name = ? AND c.table_schema = 'public'
                ORDER BY c.ordinal_position
                """;
        String pkSql = """
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                     ON tc.constraint_name = kcu.constraint_name
                     AND tc.table_schema = kcu.table_schema
                WHERE tc.constraint_type = 'PRIMARY KEY'
                  AND tc.table_name = ?
                  AND tc.table_schema = 'public'
                """;
        List<DbColumnMeta> columns = new ArrayList<>();
        long rowCount = 0;
        try (Connection conn = dataSource.getConnection()) {
            // Primary keys
            List<String> pks = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(pkSql)) {
                ps.setString(1, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) pks.add(rs.getString("column_name"));
                }
            }
            // Columns
            try (PreparedStatement ps = conn.prepareStatement(colSql)) {
                ps.setString(1, tableName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String colName = rs.getString("column_name");
                        String dataType = rs.getString("data_type");
                        Integer charLen = rs.getObject("character_maximum_length", Integer.class);
                        Integer numPrec = rs.getObject("numeric_precision", Integer.class);
                        String displayType = buildDisplayType(dataType, charLen, numPrec);
                        boolean nullable = "YES".equalsIgnoreCase(rs.getString("is_nullable"));
                        String colDefault = rs.getString("column_default");
                        columns.add(new DbColumnMeta(colName, displayType, nullable, colDefault, pks.contains(colName)));
                    }
                }
            }
            rowCount = queryRowCount(conn, tableName);
        } catch (Exception e) {
            log.warn("Error obteniendo detalle de tabla {}: {}", tableName, e.getMessage());
        }
        return new DbTableMeta(tableName, rowCount, columns);
    }

    private long queryRowCount(Connection conn, String tableName) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM \"" + tableName + "\"");
                ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            log.warn("Error contando filas de {}: {}", tableName, e.getMessage());
            return -1;
        }
    }

    private String buildDisplayType(String dataType, Integer charLen, Integer numPrec) {
        return switch (dataType) {
            case "character varying" -> charLen != null ? "varchar(%d)".formatted(charLen) : "varchar";
            case "character" -> charLen != null ? "char(%d)".formatted(charLen) : "char";
            case "numeric" -> numPrec != null ? "numeric(%d)".formatted(numPrec) : "numeric";
            default -> dataType;
        };
    }

    private boolean checkS3() {
        if (s3Bucket == null || s3Bucket.isBlank())
            return false;
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(s3Bucket).build());
            return true;
        } catch (Exception e) {
            log.warn("S3 health check failed: {}", e.getMessage());
            return false;
        }
    }
}
