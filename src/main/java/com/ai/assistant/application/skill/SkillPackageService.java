package com.ai.assistant.application.skill;

import com.ai.assistant.application.skill.dto.SkillMetadata;
import com.ai.assistant.domain.entity.SkillPackage;
import com.ai.assistant.domain.repository.SkillPackageRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.GetObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Skill 包服务 - 管理 Skill 的上传、存储、发布
 * 支持 MinIO 对象存储和本地文件系统存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillPackageService {

    private final SkillPackageRepository skillPackageRepository;
    private final SkillMetadataParser metadataParser;

    @Value("${skill.storage.path:./skills-storage}")
    private String storageBasePath;

    @Value("${skill.minio.enabled:false}")
    private boolean useMinio;

    @Value("${ai-assistant.minio.bucket:ai-assistant}")
    private String minioBucket;

    private MinioClient minioClient;

    @Autowired(required = false)
    public void setMinioClient(MinioClient minioClient) {
        this.minioClient = minioClient;
        if (minioClient != null && useMinio) {
            log.info("MinIO client is available, will use MinIO for skill storage");
        }
    }

    @PostConstruct
    public void init() {
        if (!useMinio || minioClient == null) {
            try {
                Path path = Paths.get(storageBasePath);
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                    log.info("Created skill storage directory: {}", storageBasePath);
                }
            } catch (IOException e) {
                log.error("Failed to create skill storage directory", e);
            }
            log.info("Using local file storage for skills");
        } else {
            log.info("Using MinIO for skill storage, bucket: {}", minioBucket);
        }
    }

    /**
     * 创建并发布 Skill
     */
    @Transactional
    public SkillPackage createAndPublish(
            String name,
            String description,
            String skillMdContent,
            List<String> triggerKeywords,
            List<String> requiredTools,
            MultipartFile[] scripts,
            MultipartFile[] references,
            MultipartFile[] assets,
            String createdBy
    ) {
        // 生成唯一的 skillId
        String skillId = "skill-" + UUID.randomUUID().toString().substring(0, 8);
        String storagePath = "skills/" + skillId;

        try {
            // 1. 保存 SKILL.md
            saveFile(storagePath, "SKILL.md", skillMdContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 2. 保存 scripts
            if (scripts != null) {
                for (MultipartFile script : scripts) {
                    if (!script.isEmpty()) {
                        saveFile(storagePath + "/scripts", script.getOriginalFilename(), script.getBytes());
                    }
                }
            }

            // 3. 保存 references
            if (references != null) {
                for (MultipartFile ref : references) {
                    if (!ref.isEmpty()) {
                        saveFile(storagePath + "/references", ref.getOriginalFilename(), ref.getBytes());
                    }
                }
            }

            // 4. 保存 assets
            if (assets != null) {
                for (MultipartFile asset : assets) {
                    if (!asset.isEmpty()) {
                        saveFile(storagePath + "/assets", asset.getOriginalFilename(), asset.getBytes());
                    }
                }
            }

            // 5. 解析 SKILL.md 获取元数据
            SkillMetadata metadata = metadataParser.parse(skillMdContent);

            // 6. 保存元数据到数据库
            SkillPackage skillPackage = SkillPackage.builder()
                    .skillId(skillId)
                    .name(name)
                    .description(description)
                    .storagePath(storagePath)
                    .version("1.0.0")
                    .isActive(true)
                    .createdBy(createdBy)
                    .build();

            skillPackage = skillPackageRepository.save(skillPackage);

            log.info("Created and published skill package: skillId={}, name={}, storage={}",
                    skillId, name, useMinio ? "MinIO" : "local");

            return skillPackage;
        } catch (Exception e) {
            log.error("Failed to create skill package", e);
            throw new RuntimeException("Failed to create skill package: " + e.getMessage(), e);
        }
    }

    /**
     * 保存文件到存储
     */
    private void saveFile(String path, String fileName, byte[] content) throws Exception {
        if (useMinio && minioClient != null) {
            String objectName = path + "/" + fileName;
            try (InputStream stream = new ByteArrayInputStream(content)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioBucket)
                                .object(objectName)
                                .stream(stream, content.length, -1)
                                .build());
                log.debug("Saved to MinIO: {}", objectName);
            }
        } else {
            Path fullPath = Paths.get(storageBasePath, path);
            Files.createDirectories(fullPath);
            Path filePath = fullPath.resolve(fileName);
            Files.write(filePath, content);
            log.debug("Saved to local: {}", filePath);
        }
    }

    /**
     * 删除存储中的文件
     */
    private void deleteFile(String path, String fileName) {
        try {
            if (useMinio && minioClient != null) {
                String objectName = path + "/" + fileName;
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(minioBucket)
                                .object(objectName)
                                .build());
                log.debug("Deleted from MinIO: {}", objectName);
            } else {
                Path filePath = Paths.get(storageBasePath, path, fileName);
                Files.deleteIfExists(filePath);
                log.debug("Deleted from local: {}", filePath);
            }
        } catch (Exception e) {
            log.warn("Failed to delete file: {}/{}", path, fileName, e);
        }
    }

    /**
     * 删除目录（本地存储用）
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", p);
                        }
                    });
        }
    }

    /**
     * 获取文件内容
     */
    public byte[] getFileContent(String storagePath, String fileName) {
        try {
            if (useMinio && minioClient != null) {
                String objectName = storagePath + "/" + fileName;
                try (InputStream stream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(minioBucket)
                                .object(objectName)
                                .build())) {
                    return stream.readAllBytes();
                }
            } else {
                Path filePath = Paths.get(storageBasePath, storagePath, fileName);
                return Files.readAllBytes(filePath);
            }
        } catch (Exception e) {
            log.error("Failed to get file content: {}/{}", storagePath, fileName, e);
            return null;
        }
    }

    /**
     * 获取 SKILL.md 内容
     */
    public String getSkillMdContent(SkillPackage skillPackage) {
        byte[] content = getFileContent(skillPackage.getStoragePath(), "SKILL.md");
        return content != null ? new String(content, java.nio.charset.StandardCharsets.UTF_8) : null;
    }

    /**
     * 获取所有已发布的 Skills
     */
    public List<SkillPackage> getPublishedSkills() {
        return skillPackageRepository.findByIsActiveTrue();
    }

    /**
     * 根据 skillId 获取 Skill
     */
    public SkillPackage getSkillById(String skillId) {
        return skillPackageRepository.findBySkillIdAndIsActiveTrue(skillId)
                .orElseThrow(() -> new RuntimeException("Skill not found: " + skillId));
    }

    /**
     * 删除 Skill
     */
    @Transactional
    public void deleteSkill(String skillId) {
        SkillPackage skillPackage = getSkillById(skillId);

        try {
            if (useMinio && minioClient != null) {
                // 删除 MinIO 中的目录（需要逐个删除文件）
                deleteMinioDirectory(skillPackage.getStoragePath());
            } else {
                // 删除本地文件系统中的文件
                Path skillPath = Paths.get(storageBasePath, skillPackage.getStoragePath());
                deleteDirectory(skillPath);
            }
        } catch (Exception e) {
            log.warn("Failed to delete skill files: {}", e.getMessage());
        }

        // 软删除
        skillPackage.setIsActive(false);
        skillPackageRepository.save(skillPackage);

        log.info("Deleted skill: skillId={}", skillId);
    }

    /**
     * 删除 MinIO 目录（删除所有对象）
     */
    private void deleteMinioDirectory(String prefix) throws Exception {
        // 列出并删除所有对象
        var objects = minioClient.listObjects(
                io.minio.ListObjectsArgs.builder()
                        .bucket(minioBucket)
                        .prefix(prefix + "/")
                        .build());
        for (var result : objects) {
            String objectName = result.get().objectName();
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectName)
                            .build());
        }
        log.debug("Deleted MinIO directory: {}", prefix);
    }

    /**
     * 重新加载 Skill
     */
    public SkillMetadata reloadSkill(String skillId) {
        SkillPackage skillPackage = getSkillById(skillId);
        String mdContent = getSkillMdContent(skillPackage);
        if (mdContent == null) {
            throw new RuntimeException("SKILL.md not found for skill: " + skillId);
        }
        return metadataParser.parse(mdContent);
    }

    /**
     * 获取 Skill 的完整目录结构
     */
    public Map<String, Object> getSkillDirectoryStructure(SkillPackage skillPackage) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("skillId", skillPackage.getSkillId());
        result.put("name", skillPackage.getName());

        String basePath = skillPackage.getStoragePath();

        // 获取各目录下的文件列表
        result.put("scripts", listFiles(basePath + "/scripts"));
        result.put("references", listFiles(basePath + "/references"));
        result.put("assets", listFiles(basePath + "/assets"));

        return result;
    }

    /**
     * 列出指定路径下的所有文件
     */
    private List<Map<String, Object>> listFiles(String directoryPath) {
        List<Map<String, Object>> files = new java.util.ArrayList<>();
        try {
            if (useMinio && minioClient != null) {
                var objects = minioClient.listObjects(
                        io.minio.ListObjectsArgs.builder()
                                .bucket(minioBucket)
                                .prefix(directoryPath + "/")
                                .build());
                for (var obj : objects) {
                    String objectName = obj.get().objectName();
                    String fileName = objectName.substring(objectName.lastIndexOf("/") + 1);
                    if (!fileName.isEmpty()) {
                        files.add(Map.of("name", fileName, "path", objectName));
                    }
                }
            } else {
                Path dirPath = Paths.get(storageBasePath, directoryPath);
                if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
                    Files.list(dirPath).forEach(file -> {
                        String fileName = file.getFileName().toString();
                        files.add(Map.of(
                                "name", fileName,
                                "path", directoryPath + "/" + fileName
                        ));
                    });
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list files in {}: {}", directoryPath, e.getMessage());
        }
        return files;
    }

    /**
     * 获取指定文件的详细内容
     */
    public String getFileContentByPath(String storagePath, String filePath) {
        try {
            byte[] content = getFileContent(storagePath, filePath);
            return content != null ? new String(content, java.nio.charset.StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            log.error("Failed to get file content: {}", filePath, e);
            return null;
        }
    }
}