package com.ai.assistant.interfaces.rest;

import com.ai.assistant.application.tools.DocumentGenerationService;
import com.ai.assistant.common.response.ApiResponse;
import com.ai.assistant.domain.entity.GeneratedDocument;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 文档下载控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentGenerationService documentGenerationService;

    public DocumentController(DocumentGenerationService documentGenerationService) {
        this.documentGenerationService = documentGenerationService;
    }

    /**
     * Test endpoint - health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        log.info("=== DOCUMENT CONTROLLER HEALTH CHECK REACHED ===");
        return ResponseEntity.ok("Document controller is up");
    }

    /**
     * 下载文档
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> download(@PathVariable String documentId, HttpServletRequest request) {
        log.info("Download request for documentId: {}", documentId);
        try {
            GeneratedDocument doc = documentGenerationService.findByDocumentId(documentId);
            log.info("Found document: {}", doc.getTitle());

            // 检查是否过期
            if (doc.isExpired()) {
                log.warn("Document expired: {}", documentId);
                return ResponseEntity.notFound().build();
            }

            Resource resource = documentGenerationService.getDocumentResource(doc);
            log.info("Resource path: {}", doc.getFilePath());

            // 根据文档类型设置 Content-Type
            String mimeType = doc.getMimeType();
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "application/octet-stream";
            }

            // 处理中文文件名
            String fileName = doc.getFileName();
            try {
                // URL 编码文件名
                String encodedFileName = java.net.URLEncoder.encode(doc.getTitle(), "UTF-8");
                fileName = encodedFileName + doc.getFileName().substring(doc.getFileName().lastIndexOf("."));
            } catch (Exception e) {
                // 如果编码失败，使用原始文件名
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mimeType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + fileName)
                    .header("X-Document-Id", doc.getDocumentId())
                    .header("X-Document-Title", doc.getTitle())
                    .body(resource);
        } catch (Exception e) {
            log.error("Download error for {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取文档信息
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<DocumentInfo>> getDocumentInfo(@PathVariable String documentId) {
        log.info("getDocumentInfo called with: {}", documentId);
        try {
            GeneratedDocument doc = documentGenerationService.findByDocumentId(documentId);
            log.info("Found document: {}", doc.getTitle());

            DocumentInfo info = new DocumentInfo();
            info.setDocumentId(doc.getDocumentId());
            info.setTitle(doc.getTitle());
            info.setFileName(doc.getFileName());
            info.setFileSize(doc.getFileSize());
            info.setDocumentType(doc.getDocumentType().name());
            info.setCreatedAt(doc.getCreatedAt());
            info.setExpiresAt(doc.getExpiresAt());
            info.setDownloadUrl("/api/v1/documents/" + doc.getDocumentId() + "/download");

            return ResponseEntity.ok(ApiResponse.success(info));
        } catch (Exception e) {
            log.error("Error getting document info for {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 文档信息 DTO
     */
    public static class DocumentInfo {
        private String documentId;
        private String title;
        private String fileName;
        private Long fileSize;
        private String documentType;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime expiresAt;
        private String downloadUrl;

        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public String getDocumentType() { return documentType; }
        public void setDocumentType(String documentType) { this.documentType = documentType; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(java.time.LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    }
}