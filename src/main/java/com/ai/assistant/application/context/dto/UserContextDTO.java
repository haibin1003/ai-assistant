package com.ai.assistant.application.context.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户上下文 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserContextDTO {

    private String sessionId;
    private String systemId;
    private String userId;
    private String username;
    private List<String> roles;
    private List<String> permissions;
    private String accessToken;
    private LocalDateTime expiresAt;
    private Map<String, Object> extraData;

    // Basic Auth credentials
    private String authUsername;
    private String authPassword;

    /**
     * 是否有效
     */
    public boolean isValid() {
        return expiresAt != null && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * 构建系统提示词中的用户信息
     */
    public String toSystemPromptSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户信息\n");
        sb.append("- 用户名: ").append(username != null ? username : "未知").append("\n");
        sb.append("- 用户ID: ").append(userId != null ? userId : "未知").append("\n");
        if (roles != null && !roles.isEmpty()) {
            sb.append("- 角色: ").append(String.join(", ", roles)).append("\n");
        }
        if (permissions != null && !permissions.isEmpty()) {
            sb.append("- 权限: ").append(String.join(", ", permissions)).append("\n");
        }
        return sb.toString();
    }
}
