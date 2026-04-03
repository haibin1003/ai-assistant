package com.ai.assistant.application.system.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新系统请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSystemRequest {

    @Size(max = 128, message = "系统名称最长128字符")
    private String systemName;

    @Size(max = 512, message = "图标URL最长512字符")
    private String iconUrl;

    @Size(max = 512, message = "MCP网关地址最长512字符")
    private String mcpGatewayUrl;

    private String authType;

    private String description;
}
