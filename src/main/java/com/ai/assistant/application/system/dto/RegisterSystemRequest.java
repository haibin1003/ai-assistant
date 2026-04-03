package com.ai.assistant.application.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册系统请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterSystemRequest {

    @NotBlank(message = "系统ID不能为空")
    @Size(max = 64, message = "系统ID最长64字符")
    private String systemId;

    @NotBlank(message = "系统名称不能为空")
    @Size(max = 128, message = "系统名称最长128字符")
    private String systemName;

    @Size(max = 512, message = "图标URL最长512字符")
    private String iconUrl;

    @NotBlank(message = "MCP网关地址不能为空")
    @Size(max = 512, message = "MCP网关地址最长512字符")
    private String mcpGatewayUrl;

    private String authType; // none, basic, bearer

    @NotBlank(message = "工具前缀不能为空")
    @Size(max = 32, message = "工具前缀最长32字符")
    private String toolPrefix;

    private String description;
}
