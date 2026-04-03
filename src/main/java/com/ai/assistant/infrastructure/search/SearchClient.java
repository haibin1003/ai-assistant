package com.ai.assistant.infrastructure.search;

/**
 * 搜索客户端接口
 */
public interface SearchClient {

    /**
     * 执行搜索
     * @param query 搜索关键词
     * @return 搜索结果
     */
    SearchResult search(String query);

    /**
     * 获取提供商名称
     */
    String getProviderName();
}
