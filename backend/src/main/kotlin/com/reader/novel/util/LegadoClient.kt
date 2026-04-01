package com.reader.novel.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.reader.novel.config.LegadoProperties
import com.reader.novel.dto.SearchBookResult
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Legado 服务端 HTTP/WebSocket 客户端
 *
 * 职责：
 *  1. 封装与 Legado 服务端的所有通信
 *  2. 自动处理 Basic Auth 认证（如果配置了用户名密码）
 *  3. WebSocket 连接（用于搜索书籍）
 *  4. HTTP GET/POST（用于获取章节内容、保存书籍等）
 */
@Component
class LegadoClient(private val legadoProperties: LegadoProperties) {

    private val logger = LoggerFactory.getLogger(LegadoClient::class.java)
    private val json = ObjectMapper().registerKotlinModule()

    /** OkHttp 客户端（带超时配置） */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(legadoProperties.connectTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(legadoProperties.readTimeout, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * 通过 WebSocket 搜索书籍
     *
     * Legado 搜索接口为 WebSocket，后端连接后发送关键词，
     * 收集所有返回结果（多条消息），连接关闭后返回完整结果列表。
     *
     * @param keyword 搜索关键词
     * @param timeoutSeconds 搜索超时（秒），超时后断开并返回已收集的结果
     * @return 搜索到的书籍列表
     */
    fun searchBooks(keyword: String, timeoutSeconds: Long = 15L): List<SearchBookResult> {
        val wsUrl = "${legadoProperties.wsUrl}/searchBook"
        logger.info("[Legado搜索] 开始搜索 keyword={}, wsUrl={}", keyword, wsUrl)

        val results = mutableListOf<SearchBookResult>()
        val latch = CountDownLatch(1)

        val request = buildRequest(wsUrl)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logger.debug("[Legado搜索] WebSocket 已连接，发送搜索词")
                webSocket.send("""{"key":"$keyword"}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // 每条消息是一本书的 JSON
                try {
                    val node = json.readTree(text)
                    val book = parseSearchResult(node)
                    if (book != null) {
                        results.add(book)
                        logger.debug("[Legado搜索] 收到结果 bookName={}", book.bookName)
                    }
                } catch (e: Exception) {
                    logger.warn("[Legado搜索] 解析搜索结果失败 text={}, error={}", text, e.message)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                logger.info("[Legado搜索] WebSocket 正在关闭 code={}, reason={}", code, reason)
                webSocket.close(1000, null)
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logger.error("[Legado搜索] WebSocket 连接失败 error={}", t.message)
                latch.countDown()
            }
        }

        val webSocket = httpClient.newWebSocket(request, listener)

        // 等待搜索完成或超时
        val completed = latch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            logger.warn("[Legado搜索] 搜索超时（{}秒），强制关闭连接，已收集{}条结果", timeoutSeconds, results.size)
            webSocket.close(1000, "search timeout")
        }

        logger.info("[Legado搜索] 搜索完成 keyword={}, 结果数={}", keyword, results.size)
        return results
    }

    /**
     * 将书籍保存到 Legado 服务端缓存
     * 必须先 saveBook，才能调用 getChapterList 和 getBookContent
     *
     * @param bookJson 书籍信息 JSON 字符串
     */
    fun saveBook(bookJson: String) {
        logger.info("[Legado] 保存书籍到Legado缓存")
        val body = bookJson.toRequestBody("application/json".toMediaType())
        val request = buildRequest("${legadoProperties.baseUrl}/saveBook", body)
        executeRequest(request)
    }

    /**
     * 获取书籍章节列表
     *
     * @param bookUrl Legado 原始书籍 URL
     * @return 章节列表 JSON 数组，每条包含 url、title、index 等字段
     */
    fun getChapterList(bookUrl: String): List<JsonNode> {
        val url = "${legadoProperties.baseUrl}/getChapterList?url=${encode(bookUrl)}"
        logger.info("[Legado] 获取章节列表 bookUrl={}", bookUrl)

        val response = executeGet(url)
        val root = json.readTree(response)

        // Legado 返回格式：{"isSuccess": true, "data": [...]}
        val data = root.get("data") ?: return emptyList()
        return data.toList()
    }

    /**
     * 获取指定章节的正文内容
     *
     * @param bookUrl Legado 原始书籍 URL
     * @param chapterIndex 章节索引（从0开始）
     * @return 章节正文文本
     */
    fun getBookContent(bookUrl: String, chapterIndex: Int): String {
        val url = "${legadoProperties.baseUrl}/getBookContent?url=${encode(bookUrl)}&index=$chapterIndex"
        logger.debug("[Legado] 获取章节内容 bookUrl={}, index={}", bookUrl, chapterIndex)

        val response = executeGet(url)
        val root = json.readTree(response)

        // Legado 返回格式：{"isSuccess": true, "data": "章节正文文本"}
        return root.get("data")?.asText() ?: ""
    }

    /**
     * 获取封面图片字节数组
     *
     * @param coverPath 封面路径（来自书籍信息中的 coverUrl）
     * @return 图片字节数组，失败时返回 null
     */
    fun getCoverBytes(coverPath: String): ByteArray? {
        val url = "${legadoProperties.baseUrl}/cover?path=${encode(coverPath)}"
        logger.debug("[Legado] 获取封面 coverPath={}", coverPath)

        return try {
            val request = buildRequest(url)
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    logger.warn("[Legado] 获取封面失败 code={}, url={}", response.code, url)
                    null
                }
            }
        } catch (e: Exception) {
            logger.warn("[Legado] 获取封面异常 url={}, error={}", url, e.message)
            null
        }
    }

    // ==================== 私有工具方法 ====================

    /** 构建 GET 请求（带可选 Basic Auth 头） */
    private fun buildRequest(url: String, body: RequestBody? = null): Request {
        val builder = Request.Builder().url(url)
        if (body != null) {
            builder.post(body)
        }
        // 如果配置了认证信息，添加 Basic Auth 头
        if (legadoProperties.username.isNotBlank()) {
            val credentials = "${legadoProperties.username}:${legadoProperties.password}"
            val authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
            builder.header("Authorization", authHeader)
            logger.debug("[Legado] 添加 Basic Auth 认证头")
        }
        return builder.build()
    }

    /** 执行 GET 请求并返回响应体文本 */
    private fun executeGet(url: String): String {
        val request = buildRequest(url)
        return executeRequest(request)
    }

    /** 执行请求并返回响应体文本 */
    private fun executeRequest(request: Request): String {
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.warn("[Legado] 请求失败 code={}, url={}", response.code, request.url)
                    throw IOException("Legado 返回错误码: ${response.code}")
                }
                response.body?.string() ?: ""
            }
        } catch (e: IOException) {
            logger.error("[Legado] 请求异常 url={}, error={}", request.url, e.message)
            throw RuntimeException("Legado 服务不可用: ${e.message}", e)
        }
    }

    /** URL 编码 */
    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    /** 解析搜索结果 JSON 节点为 SearchBookResult */
    private fun parseSearchResult(node: JsonNode): SearchBookResult? {
        val bookUrl = node.get("bookUrl")?.asText() ?: return null
        return SearchBookResult(
            bookUrl = bookUrl,
            bookName = node.get("name")?.asText() ?: "",
            author = node.get("author")?.asText() ?: "",
            coverUrl = node.get("coverUrl")?.asText(),
            intro = node.get("intro")?.asText(),
            kind = node.get("kind")?.asText(),
            latestChapterTitle = node.get("latestChapterTitle")?.asText(),
            origin = node.get("origin")?.asText(),
            originName = node.get("originName")?.asText()
        )
    }
}
