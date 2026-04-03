package com.ai.assistant;

import com.ai.assistant.application.tools.DocumentGenerationService;
import com.ai.assistant.application.tools.dto.GenerateDocumentRequest;
import com.ai.assistant.domain.entity.GeneratedDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DocumentGenerationServiceTest {

    @Autowired
    private DocumentGenerationService documentGenerationService;

    @Test
    public void testGenerateExcel() {
        GenerateDocumentRequest request = GenerateDocumentRequest.builder()
            .title("测试报表")
            .headers(Arrays.asList("名称", "版本", "类型"))
            .rows(Arrays.asList(
                Arrays.asList("Nginx", "1.25.4", "Docker"),
                Arrays.asList("MySQL", "8.0", "数据库")
            ))
            .sheetName("测试")
            .documentType("EXCEL")
            .build();

        GeneratedDocument doc = documentGenerationService.generateExcel(request);

        assertNotNull(doc.getDocumentId());
        assertEquals("测试报表", doc.getTitle());
        assertNotNull(doc.getFilePath());
        assertTrue(doc.getFileSize() > 0);

        System.out.println("Excel generated: " + doc.getDocumentId());
        System.out.println("File: " + doc.getFilePath());
        System.out.println("Download URL: /api/v1/documents/" + doc.getDocumentId() + "/download");

        // Verify file exists
        assertTrue(java.nio.file.Paths.get(doc.getFilePath()).toFile().exists());
    }

    @Test
    public void testGenerateWord() {
        GenerateDocumentRequest request = GenerateDocumentRequest.builder()
            .title("测试文档")
            .headers(Arrays.asList("名称", "版本"))
            .rows(Arrays.asList(
                Arrays.asList("Redis", "7.2"),
                Arrays.asList("Elasticsearch", "8.11")
            ))
            .documentType("WORD")
            .build();

        GeneratedDocument doc = documentGenerationService.generateWord(request);

        assertNotNull(doc.getDocumentId());
        assertEquals("测试文档", doc.getTitle());
        assertNotNull(doc.getFilePath());
        assertTrue(doc.getFileSize() > 0);

        System.out.println("Word generated: " + doc.getDocumentId());
        System.out.println("File: " + doc.getFilePath());
        System.out.println("Download URL: /api/v1/documents/" + doc.getDocumentId() + "/download");

        // Verify file exists
        assertTrue(java.nio.file.Paths.get(doc.getFilePath()).toFile().exists());
    }
}