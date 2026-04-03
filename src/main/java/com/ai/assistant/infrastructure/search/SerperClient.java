package com.ai.assistant.infrastructure.search;

import com.ai.assistant.application.config.ConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Serper API 客户端
 * 文档: https://serper.dev/
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SerperClient implements SearchClient {

    @Value("${ai-assistant.search.serper.api-url:https://google.serper.dev/search}")
    private String apiUrl;

    @Value("${ai-assistant.search.serper.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${SERPER_API_KEY:}")
    private String envApiKey;

    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    private OkHttpClient httpClient;

    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
        }
        return httpClient;
    }

    @Override
    public String getProviderName() {
        return "serper";
    }

    @Override
    public SearchResult search(String query) {
        // 优先从 ConfigService 获取，失败则使用环境变量
        String apiKey = configService.getApiKey("serper");
        if ((apiKey == null || apiKey.isEmpty()) && envApiKey != null && !envApiKey.isEmpty()) {
            apiKey = envApiKey;
            log.info("Using SERPER_API_KEY from environment variable");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return SearchResult.failure("Serper API key not configured. Set via PUT /api/v1/config/api-key/serper or environment variable SERPER_API_KEY");
        }

        try {
            String jsonBody = String.format("{\"q\":\"%s\"}", query.replace("\"", "\\\""));

            Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("X-API-KEY", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("Serper API error: {} - {}", response.code(), errorBody);
                    return SearchResult.failure("Search API error: " + response.code());
                }

                String responseBody = response.body().string();
                return parseResponse(query, responseBody);
            }
        } catch (Exception e) {
            log.error("Serper search failed", e);
            return SearchResult.failure("Search failed: " + e.getMessage());
        }
    }

    private SearchResult parseResponse(String query, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<SearchResult.SearchItem> items = new ArrayList<>();

            // 解析 organic 结果
            JsonNode organic = root.path("organic");
            if (organic.isArray()) {
                for (JsonNode node : organic) {
                    items.add(SearchResult.SearchItem.builder()
                        .title(node.path("title").asText())
                        .link(node.path("link").asText())
                        .snippet(node.path("snippet").asText())
                        .source("google")
                        .build());
                }
            }

            // 解析 news 结果（如果有）
            JsonNode news = root.path("news");
            if (news.isArray()) {
                for (JsonNode node : news) {
                    items.add(SearchResult.SearchItem.builder()
                        .title(node.path("title").asText())
                        .link(node.path("link").asText())
                        .snippet(node.path("snippet").asText())
                        .source("news")
                        .build());
                }
            }

            log.info("Serper search returned {} results for query: {}", items.size(), query);
            return SearchResult.success(query, items);
        } catch (Exception e) {
            log.error("Failed to parse Serper response", e);
            return SearchResult.failure("Failed to parse response: " + e.getMessage());
        }
    }
}
