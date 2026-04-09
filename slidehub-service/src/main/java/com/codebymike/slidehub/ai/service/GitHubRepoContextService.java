package com.codebymike.slidehub.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GitHubRepoContextService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepoContextService.class);

    private final WebClient gitHubClient;

    @Value("${slidehub.github.token:}")
    private String githubToken;

    @Value("${slidehub.github.max-files-for-context:120}")
    private int maxFilesForContext;

    @Value("${slidehub.github.max-file-size-bytes:200000}")
    private long maxFileSizeBytes;

    public GitHubRepoContextService(
            @Value("${slidehub.github.api-base-url:https://api.github.com}") String apiBaseUrl) {
        this.gitHubClient = WebClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public Optional<RepositorySnapshot> resolveSnapshot(String repoUrl) {
        RepositoryRef ref = parseRepositoryRef(repoUrl);
        if (ref == null) {
            return Optional.empty();
        }

        try {
            Map<?, ?> repoData = get("/repos/{owner}/{repo}", ref.owner(), ref.repo());
            String defaultBranch = toStringSafe(repoData.get("default_branch"));
            if (defaultBranch.isBlank()) {
                log.debug("No default branch found for {}", ref.normalizedUrl());
                return Optional.empty();
            }

            Map<?, ?> branchData = get("/repos/{owner}/{repo}/branches/{branch}",
                    ref.owner(), ref.repo(), defaultBranch);
            Map<?, ?> commit = toMap(branchData.get("commit"));
            String headSha = toStringSafe(commit.get("sha"));
            Map<?, ?> nestedCommit = toMap(commit.get("commit"));
            Map<?, ?> tree = toMap(nestedCommit.get("tree"));
            String treeSha = toStringSafe(tree.get("sha"));

            if (headSha.isBlank() || treeSha.isBlank()) {
                log.debug("Missing head/tree sha for {}", ref.normalizedUrl());
                return Optional.empty();
            }

            Map<?, ?> treeData = get("/repos/{owner}/{repo}/git/trees/{treeSha}?recursive=1",
                    ref.owner(), ref.repo(), treeSha);

            List<RepoFile> files = extractFiles(treeData);
            String context = buildContextText(ref, defaultBranch, headSha, files);

            return Optional.of(new RepositorySnapshot(
                    ref.normalizedUrl(),
                    ref.owner(),
                    ref.repo(),
                    defaultBranch,
                    headSha,
                    context));
        } catch (Exception e) {
            log.warn("No se pudo resolver contexto GitHub para {}: {}", repoUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private RepositoryRef parseRepositoryRef(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(repoUrl.trim());
            String host = uri.getHost();
            if (host == null || !host.equalsIgnoreCase("github.com")) {
                return null;
            }

            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            String cleaned = path.startsWith("/") ? path.substring(1) : path;
            if (cleaned.endsWith(".git")) {
                cleaned = cleaned.substring(0, cleaned.length() - 4);
            }

            String[] segments = cleaned.split("/");
            if (segments.length < 2) {
                return null;
            }

            String owner = segments[0];
            String repo = segments[1];
            String normalized = "https://github.com/" + owner + "/" + repo;
            return new RepositoryRef(owner, repo, normalized);
        } catch (Exception e) {
            log.debug("URL inválida para parse GitHub '{}': {}", repoUrl, e.getMessage());
            return null;
        }
    }

    private List<RepoFile> extractFiles(Map<?, ?> treeData) {
        List<?> rawTree = (List<?>) treeData.get("tree");
        if (rawTree == null) {
            return List.of();
        }

        List<RepoFile> files = new ArrayList<>();
        for (Object item : rawTree) {
            Map<?, ?> entry = toMap(item);
            if (!"blob".equals(toStringSafe(entry.get("type")))) {
                continue;
            }
            String path = toStringSafe(entry.get("path"));
            if (path.isBlank()) {
                continue;
            }
            long size = toLongSafe(entry.get("size"));

            if (isExcludedPath(path)) {
                continue;
            }
            if (!isRelevantPath(path)) {
                continue;
            }
            if (size > maxFileSizeBytes) {
                continue;
            }

            files.add(new RepoFile(path, size));
        }

        files.sort(Comparator.comparing(RepoFile::path));
        if (files.size() > maxFilesForContext) {
            return files.subList(0, maxFilesForContext);
        }
        return files;
    }

    private boolean isRelevantPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        String fileName = fileName(normalized);

        if (normalized.startsWith("docs/") || normalized.contains("/docs/")) {
            return true;
        }

        if (fileName.startsWith("readme")
                || fileName.startsWith("changelog")
                || fileName.startsWith("contributing")
                || fileName.startsWith("license")
                || fileName.startsWith("notice")) {
            return true;
        }

        if (isKnownBuildOrConfigFile(fileName)) {
            return true;
        }

        return hasDocumentationExtension(fileName);
    }

    private boolean isExcludedPath(String path) {
        String normalized = path.toLowerCase(Locale.ROOT);
        return normalized.startsWith("node_modules/") || normalized.contains("/node_modules/")
                || normalized.startsWith("target/") || normalized.contains("/target/")
                || normalized.startsWith("dist/") || normalized.contains("/dist/")
                || normalized.startsWith("build/") || normalized.contains("/build/")
                || normalized.startsWith(".git/") || normalized.contains("/.git/")
                || normalized.startsWith(".idea/") || normalized.contains("/.idea/")
                || normalized.startsWith(".vscode/") || normalized.contains("/.vscode/")
                || normalized.startsWith("coverage/") || normalized.contains("/coverage/")
                || normalized.startsWith("vendor/") || normalized.contains("/vendor/")
                || normalized.startsWith(".next/") || normalized.contains("/.next/")
                || normalized.startsWith("out/") || normalized.contains("/out/");
    }

    private boolean isKnownBuildOrConfigFile(String fileName) {
        return fileName.equals("pom.xml")
                || fileName.equals("build.gradle")
                || fileName.equals("build.gradle.kts")
                || fileName.equals("settings.gradle")
                || fileName.equals("settings.gradle.kts")
                || fileName.equals("gradle.properties")
                || fileName.equals("package.json")
                || fileName.equals("package-lock.json")
                || fileName.equals("yarn.lock")
                || fileName.equals("pnpm-lock.yaml")
                || fileName.equals("pnpm-workspace.yaml")
                || fileName.equals("npm-shrinkwrap.json")
                || fileName.equals("dockerfile")
                || fileName.equals("docker-compose.yml")
                || fileName.equals("docker-compose.yaml")
                || fileName.equals("compose.yml")
                || fileName.equals("compose.yaml")
                || fileName.equals(".env.example")
                || fileName.equals(".env.sample")
                || fileName.equals(".nvmrc")
                || fileName.equals("mvnw")
                || fileName.equals("mvnw.cmd");
    }

    private boolean hasDocumentationExtension(String fileName) {
        return fileName.endsWith(".md")
                || fileName.endsWith(".adoc")
                || fileName.endsWith(".rst")
                || fileName.endsWith(".txt")
                || fileName.endsWith(".yaml")
                || fileName.endsWith(".yml")
                || fileName.endsWith(".toml")
                || fileName.endsWith(".ini");
    }

    private String fileName(String path) {
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            return path;
        }
        return path.substring(idx + 1);
    }

    private String buildContextText(RepositoryRef ref, String defaultBranch, String headSha, List<RepoFile> files) {
        Map<String, Integer> extensionFrequency = new LinkedHashMap<>();
        LinkedHashSet<String> rootFiles = new LinkedHashSet<>();
        LinkedHashSet<String> topFolders = new LinkedHashSet<>();

        for (RepoFile file : files) {
            String path = file.path();
            if (!path.contains("/")) {
                rootFiles.add(path);
            } else {
                topFolders.add(path.substring(0, path.indexOf('/')));
            }
            String ext = extension(path);
            if (!ext.isBlank()) {
                extensionFrequency.merge(ext, 1, Integer::sum);
            }
        }

        String extensionSummary = extensionFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(", "));

        String fileList = files.stream()
                .map(RepoFile::path)
                .collect(Collectors.joining("\n- ", "- ", ""));

        return """
                Snapshot de repositorio (vía GitHub API):
                - repo: %s
                - branch: %s
                - commit: %s
                - totalFilesSampled: %d
                - rootFiles: %s
                - topFolders: %s
                - extensions: %s

                Lista de archivos analizados:
                %s
                """.formatted(
                ref.normalizedUrl(),
                defaultBranch,
                headSha,
                files.size(),
                String.join(", ", rootFiles),
                String.join(", ", topFolders),
                extensionSummary,
                fileList);
    }

    private String extension(String path) {
        int idx = path.lastIndexOf('.');
        if (idx < 0 || idx == path.length() - 1) {
            return "";
        }
        return path.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private Map<?, ?> get(String uriTemplate, Object... uriVariables) {
        WebClient.RequestHeadersSpec<?> request = gitHubClient.get().uri(uriTemplate, uriVariables);
        if (githubToken != null && !githubToken.isBlank()) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken.trim());
        }
        return request
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private Map<?, ?> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Map.of();
    }

    private long toLongSafe(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private String toStringSafe(Object value) {
        return Objects.toString(value, "");
    }

    private record RepositoryRef(String owner, String repo, String normalizedUrl) {
    }

    private record RepoFile(String path, long size) {
    }

    public record RepositorySnapshot(
            String normalizedRepoUrl,
            String owner,
            String repo,
            String defaultBranch,
            String headCommitSha,
            String contextText,
            int fileCount) {
    }
}