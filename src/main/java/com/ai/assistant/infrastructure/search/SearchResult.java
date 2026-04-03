package com.ai.assistant.infrastructure.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private boolean success;
    private String query;
    private List<SearchItem> items;
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchItem {
        private String title;
        private String link;
        private String snippet;
        private String source;
    }

    public static SearchResult success(String query, List<SearchItem> items) {
        return SearchResult.builder()
            .success(true)
            .query(query)
            .items(items)
            .build();
    }

    public static SearchResult failure(String error) {
        return SearchResult.builder()
            .success(false)
            .error(error)
            .build();
    }
}
