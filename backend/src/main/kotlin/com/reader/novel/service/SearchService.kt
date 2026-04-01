package com.reader.novel.service

import com.reader.novel.config.LegadoProperties
import com.reader.novel.dto.SearchResponse
import com.reader.novel.util.LegadoClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 搜索服务
 *
 * 通过后端调用 Legado WebSocket 接口搜索书籍，
 * 收集结果后返回给前端。
 * 前端不直接连接 Legado，所有搜索经过本服务中转。
 */
@Service
class SearchService(
    private val legadoClient: LegadoClient,
    private val legadoProperties: LegadoProperties
) {
    private val logger = LoggerFactory.getLogger(SearchService::class.java)

    /**
     * 搜索书籍
     *
     * @param keyword 搜索关键词
     * @param timeoutSeconds 搜索超时（秒）
     * @return 搜索结果列表
     */
    fun searchBooks(keyword: String, timeoutSeconds: Long = 15L): SearchResponse {
        logger.info("[搜索服务] 开始搜索 keyword={}, timeout={}s", keyword, timeoutSeconds)

        val effectiveTimeout = timeoutSeconds.coerceIn(5L, legadoProperties.searchTimeout)
        val results = legadoClient.searchBooks(keyword, effectiveTimeout)

        logger.info("[搜索服务] 搜索完成 keyword={}, 结果数={}", keyword, results.size)
        return SearchResponse(list = results, total = results.size)
    }
}
