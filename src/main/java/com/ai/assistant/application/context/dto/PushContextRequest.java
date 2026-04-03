package com.ai.assistant.application.context.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户上下文推送请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushContextRequest {

    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotBlank(message = "系统ID不能为空")
    private String systemId;

    @NotNull(message = "用户信息不能为空")
    private UserInfo user;

    private Credentials credentials;

    /**
     * 用户信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        @NotBlank(message = "用户ID不能为空")
        private String id;

        @NotBlank(message = "用户名不能为空")
        private String username;

        private List<String> roles;
        private List<String> permissions;
        private String realName;
        private String email;
    }

    /**
     * 认证凭据
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Credentials {
        private String accessToken;
        private String refreshToken;
        private Long expiresIn; // 秒

        // Basic Auth credentials
        private String username;
        private String password;
    }
}
