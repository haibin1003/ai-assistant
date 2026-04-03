package com.ai.assistant.application.tools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档生成请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateDocumentRequest {

    /**
     * 文档标题
     */
    private String title;

    /**
     * 列标题，如 ["软件名称", "版本", "类型"]
     */
    private List<String> headers;

    /**
     * 数据行，如 [["Nginx", "1.25.4", "Docker镜像"], ...]
     */
    private List<List<String>> rows;

    /**
     * 工作表名称（Excel）/文档标题（Word），默认 "Sheet1"
     */
    @Builder.Default
    private String sheetName = "Sheet1";

    /**
     * 文档类型：EXCEL 或 WORD
     */
    private String documentType;
}