package com.ai.assistant.interfaces.rest;

import com.ai.assistant.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 认证控制器 - 处理用户登录
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    // 硬编码本地 OSRM 地址用于开发测试
    private static final String OSRM_BASE_URL = "http://localhost:8080";

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody LoginRequest request) {

        log.info("Login request for username: {}", request.getUsername());

        try {
            // 1. 调用 OSRM 后端验证用户并获取 token
            String osrmUrl = OSRM_BASE_URL + "/api/v1/auth/login";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> httpRequest = new HttpEntity<>(
                Map.of("username", request.getUsername(), "password", request.getPassword()),
                headers
            );

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.postForEntity(osrmUrl, httpRequest, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> osrmResponse = response.getBody();

                // OSRM returns data in "data" field
                Map<String, Object> data = (Map<String, Object>) osrmResponse.get("data");
                if (data == null) {
                    data = osrmResponse;
                }

                // 提取 token 和用户信息 - OSRM 返回 accessToken 在 data 字段中
                Object tokenObj = data.get("accessToken");
                Object rolesObj = data.get("roles");

                String token = tokenObj != null ? tokenObj.toString() : null;

                // 从 user 对象获取 userId 和 username
                Long userId = 0L;
                String username = request.getUsername();
                if (data.containsKey("user")) {
                    Object userObj = data.get("user");
                    if (userObj instanceof Map) {
                        Map<String, Object> userMap = (Map<String, Object>) userObj;
                        if (userMap.get("id") != null) {
                            userId = Long.parseLong(userMap.get("id").toString());
                        }
                        if (userMap.get("username") != null) {
                            username = userMap.get("username").toString();
                        }
                        // 从 user.roles 覆盖 roles
                        if (userMap.get("roles") != null) {
                            rolesObj = userMap.get("roles");
                        }
                    }
                }

                // 处理 roles
                String rolesStr = "DEVELOPER";
                if (rolesObj instanceof java.util.List) {
                    rolesStr = String.join(",", (java.util.List<String>) rolesObj);
                }

                log.info("Login success: username={}, userId={}, roles={}", username, userId, rolesStr);

                // 2. 返回给前端
                Map<String, Object> result = Map.of(
                    "token", token != null ? token : "",
                    "userId", userId,
                    "username", username,
                    "roles", rolesStr
                );

                return ResponseEntity.ok(ApiResponse.success(result));
            } else if (response.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("LOGIN_FAILED", "用户名或密码错误"));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("LOGIN_FAILED", "登录失败：无效的响应"));
            }

        } catch (Exception e) {
            log.error("Login failed for username: {}", request.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("LOGIN_FAILED", "登录失败：" + e.getMessage()));
        }
    }

    /**
     * 登录请求
     */
    @lombok.Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}