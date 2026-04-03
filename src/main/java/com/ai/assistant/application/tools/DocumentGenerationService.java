package com.ai.assistant.application.tools;

import com.ai.assistant.application.tools.dto.GenerateDocumentRequest;
import com.ai.assistant.domain.entity.GeneratedDocument;
import com.ai.assistant.domain.entity.GeneratedDocument.DocumentType;
import com.ai.assistant.domain.repository.GeneratedDocumentRepository;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文档生成服务 - 使用 Apache POI 生成 Excel 和 Word 文档
 */
@Slf4j
@Service
public class DocumentGenerationService {

    private final GeneratedDocumentRepository documentRepository;

    @Value("${document.storage.path:${user.dir}/documents}")
    private String storagePath;

    @Value("${document.expire.hours:24}")
    private int expireHours;

    @Autowired(required = false)
    private MinioClient minioClient;

    @Value("${ai-assistant.minio.bucket:ai-assistant}")
    private String minioBucket;

    @Value("${ai-assistant.document.minio.enabled:false}")
    private boolean useMinio;

    public DocumentGenerationService(GeneratedDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @PostConstruct
    public void init() throws IOException {
        // 确保存储目录存在，使用绝对路径
        String absolutePath = Paths.get(storagePath).toAbsolutePath().toString();
        Path path = Paths.get(absolutePath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("创建文档存储目录: {}", absolutePath);
        }
    }

    /**
     * 上传文件到 MinIO
     */
    private String uploadToMinio(byte[] content, String objectName, String mimeType) throws Exception {
        if (minioClient == null || !useMinio) {
            throw new RuntimeException("MinIO is not enabled");
        }
        try (InputStream stream = new ByteArrayInputStream(content)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(objectName)
                            .stream(stream, content.length, -1)
                            .contentType(mimeType)
                            .build());
            log.info("Uploaded to MinIO: {}/{}", minioBucket, objectName);
            return minioBucket + "/" + objectName;
        }
    }

    /**
     * 从 MinIO 获取文件
     */
    private byte[] getFromMinio(String objectName) throws Exception {
        if (minioClient == null) {
            throw new RuntimeException("MinIO client is not available");
        }
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(objectName)
                        .build())) {
            return stream.readAllBytes();
        }
    }

    /**
     * 获取 MinIO presigned 下载 URL
     */
    private String getMinioDownloadUrl(String objectName) throws Exception {
        if (minioClient == null) {
            throw new RuntimeException("MinIO client is not available");
        }
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(minioBucket)
                        .object(objectName)
                        .expiry(24, TimeUnit.HOURS)
                        .build());
    }

    /**
     * 检查是否是 MinIO 路径
     */
    private boolean isMinioPath(String filePath) {
        return filePath != null && filePath.contains("/");
    }

    /**
     * 从文件路径提取 MinIO object name
     */
    private String extractMinioObjectName(String filePath) {
        if (filePath == null) return null;
        int slashIndex = filePath.indexOf('/');
        return slashIndex >= 0 ? filePath.substring(slashIndex + 1) : filePath;
    }

    /**
     * 生成 Excel 文档
     */
    public GeneratedDocument generateExcel(GenerateDocumentRequest request) {
        String documentId = UUID.randomUUID().toString();
        String fileName = documentId + ".xlsx";
        String absolutePath = Paths.get(storagePath).toAbsolutePath().toString();
        Path filePath = Paths.get(absolutePath, fileName);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            // 创建工作表
            Sheet sheet = workbook.createSheet(request.getSheetName() != null ? request.getSheetName() : "Sheet1");

            // 设置列宽（根据标题长度）
            if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
                for (int i = 0; i < request.getHeaders().size(); i++) {
                    sheet.setColumnWidth(i, 20 * 256); // 默认宽度 20
                }
            }

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontName("微软雅黑");
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // 创建数据单元格样式
            CellStyle dataStyle = workbook.createCellStyle();
            Font dataFont = workbook.createFont();
            dataFont.setFontName("微软雅黑");
            dataStyle.setFont(dataFont);
            dataStyle.setAlignment(HorizontalAlignment.LEFT);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 写入表头
            if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < request.getHeaders().size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(request.getHeaders().get(i));
                    cell.setCellStyle(headerStyle);
                }
            }

            // 写入数据行
            if (request.getRows() != null && !request.getRows().isEmpty()) {
                for (int rowIdx = 0; rowIdx < request.getRows().size(); rowIdx++) {
                    List<String> rowData = request.getRows().get(rowIdx);
                    Row row = sheet.createRow(rowIdx + 1);
                    if (rowData != null) {
                        for (int colIdx = 0; colIdx < rowData.size(); colIdx++) {
                            Cell cell = row.createCell(colIdx);
                            String cellValue = rowData.get(colIdx);
                            cell.setCellValue(cellValue != null ? cellValue : "");
                            cell.setCellStyle(dataStyle);
                        }
                    }
                }
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }

            log.info("生成 Excel 文档成功: {}, 文件大小: {} bytes", fileName, Files.size(filePath));

        } catch (Exception e) {
            log.error("生成 Excel 文档失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成 Excel 文档失败: " + e.getMessage(), e);
        }

        // 保存文档记录
        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            fileSize = 0L;
        }
        return saveDocumentRecord(documentId, DocumentType.EXCEL, request.getTitle(),
                fileName, filePath.toString(), fileSize, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * 生成 Word 文档
     */
    public GeneratedDocument generateWord(GenerateDocumentRequest request) {
        String documentId = UUID.randomUUID().toString();
        String fileName = documentId + ".docx";
        String absolutePath = Paths.get(storagePath).toAbsolutePath().toString();
        Path filePath = Paths.get(absolutePath, fileName);

        try (XWPFDocument document = new XWPFDocument()) {
            // 添加标题
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(request.getTitle());
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setFontFamily("微软雅黑");

            // 添加空行
            document.createParagraph();

            // 创建表格
            if (request.getHeaders() != null && !request.getHeaders().isEmpty() && request.getRows() != null) {
                XWPFTable table = document.createTable(request.getRows().size() + 1, request.getHeaders().size());

                // 写入表头
                XWPFTableRow headerRow = table.getRow(0);
                for (int i = 0; i < request.getHeaders().size(); i++) {
                    XWPFTableCell cell = headerRow.getCell(i);
                    cell.setText(request.getHeaders().get(i));
                    // 设置表头样式
                    XWPFParagraph cellPara = cell.getParagraphs().get(0);
                    cellPara.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun cellRun = cellPara.createRun();
                    cellRun.setBold(true);
                    cellRun.setFontFamily("微软雅黑");
                }

                // 写入数据行
                for (int rowIdx = 0; rowIdx < request.getRows().size(); rowIdx++) {
                    List<String> rowData = request.getRows().get(rowIdx);
                    XWPFTableRow tableRow = table.getRow(rowIdx + 1);
                    if (rowData != null) {
                        for (int colIdx = 0; colIdx < rowData.size(); colIdx++) {
                            // 确保行有足够的单元格
                            XWPFTableCell cell;
                            if (colIdx < tableRow.getTableCells().size()) {
                                cell = tableRow.getCell(colIdx);
                            } else {
                                cell = tableRow.addNewTableCell();
                            }
                            String cellText = rowData.get(colIdx);
                            cell.setText(cellText != null ? cellText : "");
                            XWPFParagraph cellPara = cell.getParagraphs().get(0);
                            XWPFRun cellRun = cellPara.createRun();
                            cellRun.setFontFamily("微软雅黑");
                        }
                    }
                }
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                document.write(fos);
            }

            log.info("生成 Word 文档成功: {}, 文件大小: {} bytes", fileName, Files.size(filePath));

        } catch (Exception e) {
            log.error("生成 Word 文档失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成 Word 文档失败: " + e.getMessage(), e);
        }

        // 保存文档记录
        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            fileSize = 0L;
        }
        return saveDocumentRecord(documentId, DocumentType.WORD, request.getTitle(),
                fileName, filePath.toString(), fileSize, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    /**
     * 保存文档记录到数据库
     */
    private GeneratedDocument saveDocumentRecord(String documentId, DocumentType type, String title,
                                                   String fileName, String filePath, Long fileSize, String mimeType) {
        GeneratedDocument doc = GeneratedDocument.builder()
                .documentId(documentId)
                .documentType(type)
                .title(title)
                .fileName(fileName)
                .filePath(filePath)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .expiresAt(LocalDateTime.now().plusHours(expireHours))
                .build();

        return documentRepository.save(doc);
    }

    /**
     * 保存已生成的文件记录（由外部生成文件后调用）
     * 如果启用 MinIO，则上传到 MinIO 存储
     */
    public GeneratedDocument saveExistingFile(String documentId, String title, String fileName,
                                               String filePath, long fileSize, DocumentType type) {
        String mimeType;
        if (type == DocumentType.EXCEL) {
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (type == DocumentType.WORD) {
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else {
            mimeType = "text/markdown;charset=utf-8";
        }

        // 如果启用 MinIO，上传文件
        log.info("saveExistingFile: useMinio={}, minioClient={}", useMinio, minioClient != null);
        if (useMinio && minioClient != null) {
            try {
                String objectName = "documents/" + documentId + "/" + fileName;
                byte[] content = Files.readAllBytes(Paths.get(filePath));
                String minioPath = uploadToMinio(content, objectName, mimeType);
                log.info("文件已上传到 MinIO: {}", minioPath);
                // 返回包含 MinIO 路径的记录，格式: minio://bucket/path
                return saveDocumentRecord(documentId, type, title, fileName, minioPath, fileSize, mimeType);
            } catch (Exception e) {
                log.error("上传到 MinIO 失败，使用本地存储: {}", e.getMessage());
                // 失败时回退到本地存储
            }
        }

        return saveDocumentRecord(documentId, type, title, fileName, filePath, fileSize, mimeType);
    }

    /**
     * 直接保存内容到 MinIO（不依赖本地文件）
     */
    public GeneratedDocument saveContent(String documentId, String title, String fileName,
                                          byte[] content, DocumentType type) {
        String mimeType;
        if (type == DocumentType.EXCEL) {
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (type == DocumentType.WORD) {
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else {
            mimeType = "text/markdown;charset=utf-8";
        }

        // 如果启用 MinIO，上传文件
        log.info("saveExistingFile: useMinio={}, minioClient={}", useMinio, minioClient != null);
        if (useMinio && minioClient != null) {
            try {
                String objectName = "documents/" + documentId + "/" + fileName;
                String minioPath = uploadToMinio(content, objectName, mimeType);
                log.info("文件已上传到 MinIO: {}", minioPath);
                return saveDocumentRecord(documentId, type, title, fileName, minioPath, (long) content.length, mimeType);
            } catch (Exception e) {
                log.error("上传到 MinIO 失败: {}", e.getMessage());
                throw new RuntimeException("上传到 MinIO 失败: " + e.getMessage(), e);
            }
        }

        // MinIO 未启用时，保存到本地
        String absolutePath = Paths.get(storagePath).toAbsolutePath().toString();
        Path localPath = Paths.get(absolutePath, documentId + "_" + fileName);
        try {
            Files.write(localPath, content);
            return saveDocumentRecord(documentId, type, title, fileName, localPath.toString(), (long) content.length, mimeType);
        } catch (IOException e) {
            throw new RuntimeException("保存文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 documentId 获取文档记录
     */
    public GeneratedDocument findByDocumentId(String documentId) {
        return documentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + documentId));
    }

    /**
     * 获取文档下载资源
     */
    public Resource getDocumentResource(GeneratedDocument doc) {
        String filePath = doc.getFilePath();

        // 处理 MinIO 路径 (格式: bucket/objectName)
        if (filePath != null && filePath.contains("/") && !filePath.startsWith("/")) {
            try {
                String objectName = extractMinioObjectName(filePath);
                byte[] content = getFromMinio(objectName);
                // 写入临时文件返回
                Path tempFile = Files.createTempFile("download_", doc.getFileName());
                Files.write(tempFile, content);
                return new FileSystemResource(tempFile);
            } catch (Exception e) {
                log.error("从 MinIO 获取文件失败: {}", e.getMessage());
                throw new RuntimeException("获取文件失败: " + e.getMessage());
            }
        }

        // 本地文件路径
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
        }
        return new FileSystemResource(path);
    }

    /**
     * 获取文档下载 URL（支持 MinIO presigned URL）
     */
    public String getDownloadUrl(GeneratedDocument doc) {
        String filePath = doc.getFilePath();

        // 处理 MinIO 路径
        if (filePath != null && filePath.contains("/") && !filePath.startsWith("/")) {
            try {
                String objectName = extractMinioObjectName(filePath);
                return getMinioDownloadUrl(objectName);
            } catch (Exception e) {
                log.error("获取 MinIO 下载 URL 失败: {}", e.getMessage());
            }
        }

        // 本地文件返回相对路径
        return "/api/v1/documents/" + doc.getDocumentId() + "/download";
    }

    /**
     * 清理过期文档
     */
    public int cleanupExpiredDocuments() {
        int deleted = documentRepository.deleteExpiredDocuments(LocalDateTime.now());
        log.info("清理过期文档: {} 条", deleted);
        return deleted;
    }
}