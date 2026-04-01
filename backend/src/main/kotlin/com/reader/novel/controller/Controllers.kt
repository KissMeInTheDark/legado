package com.reader.novel.controller

import com.reader.novel.dto.*
import com.reader.novel.service.*
import com.reader.novel.util.getCurrentUserId
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.*

/**
 * 用户接口控制器
 *
 * 路径前缀：/user
 * 无需认证：/user/login, /user/refresh-token
 */
@RestController
@RequestMapping("/user")
class UserController(private val userService: UserService) {

    private val logger = LoggerFactory.getLogger(UserController::class.java)

    /**
     * 用户登录/注册（设备一键登录）
     * 调用时机：App 启动时，本地无 token 或 token 过期时调用
     */
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ApiResponse<LoginResponse> {
        logger.info("[用户接口] 登录 deviceId={}", request.deviceId)
        return ApiResponse.success(userService.loginOrRegister(request))
    }

    /**
     * 刷新 JWT Token
     * 调用时机：收到 401 响应时自动调用
     */
    @PostMapping("/refresh-token")
    fun refreshToken(@RequestBody request: RefreshTokenRequest): ApiResponse<LoginResponse> {
        logger.info("[用户接口] 刷新Token")
        return ApiResponse.success(userService.refreshToken(request))
    }
}

/**
 * 搜索接口控制器
 *
 * 路径前缀：/search
 * 需要认证
 */
@RestController
@RequestMapping("/search")
class SearchController(private val searchService: SearchService) {

    private val logger = LoggerFactory.getLogger(SearchController::class.java)

    /**
     * 搜索书籍
     * 调用时机：用户在搜索页输入关键词并点击搜索时
     *
     * @param keyword 搜索关键词
     * @param timeout 超时时间（秒），默认15，最大30
     */
    @GetMapping("/books")
    fun searchBooks(
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "15") timeout: Long
    ): ApiResponse<SearchResponse> {
        logger.info("[搜索接口] keyword={}, timeout={}s", keyword, timeout)
        return ApiResponse.success(searchService.searchBooks(keyword, timeout))
    }
}

/**
 * 书架接口控制器
 *
 * 路径前缀：/bookshelf
 * 需要认证
 */
@RestController
@RequestMapping("/bookshelf")
class BookshelfController(private val bookService: BookService) {

    private val logger = LoggerFactory.getLogger(BookshelfController::class.java)

    /**
     * 获取当前用户的书架列表（含阅读进度）
     * 调用时机：书架页面加载时
     */
    @GetMapping
    fun getBookshelf(): ApiResponse<List<BookshelfItemResponse>> {
        val userId = getCurrentUserId()
        logger.info("[书架接口] 获取书架 userId={}", userId)
        return ApiResponse.success(bookService.getBookshelf(userId))
    }

    /**
     * 从书架移除书籍
     * 调用时机：用户长按书籍并选择「移除书架」时
     */
    @DeleteMapping("/{bookId}")
    fun removeBook(@PathVariable bookId: String): ApiResponse<String> {
        val userId = getCurrentUserId()
        logger.info("[书架接口] 移除书籍 userId={}, bookId={}", userId, bookId)
        bookService.removeFromBookshelf(userId, bookId)
        return ApiResponse.success("已从书架移除")
    }
}

/**
 * 书籍接口控制器
 *
 * 路径前缀：/books
 * 需要认证
 */
@RestController
@RequestMapping("/books")
class BookController(
    private val bookService: BookService,
    private val chapterService: ChapterService
) {
    private val logger = LoggerFactory.getLogger(BookController::class.java)

    /**
     * 将书籍加入书架并触发后台下载
     * 调用时机：用户在搜索结果中点击「阅读」后调用
     */
    @PostMapping("/add")
    fun addBook(@RequestBody request: AddBookRequest): ApiResponse<AddBookResponse> {
        val userId = getCurrentUserId()
        logger.info("[书籍接口] 加入书架 userId={}, bookName={}", userId, request.bookName)
        return ApiResponse.success(bookService.addBook(userId, request))
    }

    /**
     * 查询书籍下载进度
     * 调用时机：加入书架后，前端轮询此接口直到下载完成
     */
    @GetMapping("/{bookId}/download-status")
    fun getDownloadStatus(@PathVariable bookId: String): ApiResponse<DownloadStatusResponse> {
        logger.debug("[书籍接口] 查询下载进度 bookId={}", bookId)
        return ApiResponse.success(bookService.getDownloadStatus(bookId))
    }

    /**
     * 获取书籍章节列表（分页）
     * 调用时机：用户进入章节列表页时（前端应缓存到本地）
     */
    @GetMapping("/{bookId}/chapters")
    fun getChapterList(
        @PathVariable bookId: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "200") size: Int
    ): ApiResponse<ChapterListResponse> {
        logger.info("[书籍接口] 获取章节列表 bookId={}, page={}, size={}", bookId, page, size)
        return ApiResponse.success(bookService.getChapterList(bookId, page, size))
    }

    /**
     * 获取单个章节内容的 OSS 预签名 URL
     * 调用时机：阅读器需要加载某章节时
     */
    @GetMapping("/{bookId}/chapters/{chapterIdx}/url")
    fun getChapterUrl(
        @PathVariable bookId: String,
        @PathVariable chapterIdx: Int
    ): ApiResponse<ChapterUrlResponse> {
        logger.debug("[书籍接口] 获取章节URL bookId={}, idx={}", bookId, chapterIdx)
        return ApiResponse.success(chapterService.getChapterUrl(bookId, chapterIdx))
    }

    /**
     * 批量获取章节预签名 URL（用于预加载相邻章节）
     * 调用时机：阅读器加载当前章节时，同步预加载前后各2章
     */
    @PostMapping("/{bookId}/chapters/batch-urls")
    fun getBatchChapterUrls(
        @PathVariable bookId: String,
        @RequestBody request: BatchChapterUrlRequest
    ): ApiResponse<BatchChapterUrlResponse> {
        logger.debug("[书籍接口] 批量获取章节URL bookId={}, idxList={}", bookId, request.chapterIdxList)
        return ApiResponse.success(chapterService.getBatchChapterUrls(bookId, request.chapterIdxList))
    }
}

/**
 * 阅读进度接口控制器
 *
 * 路径前缀：/progress
 * 需要认证
 */
@RestController
@RequestMapping("/progress")
class ProgressController(private val chapterService: ChapterService) {

    private val logger = LoggerFactory.getLogger(ProgressController::class.java)

    /**
     * 上报阅读进度
     * 调用时机：翻页时（节流30秒）、退出阅读器时立即上报、App 进入后台时
     */
    @PostMapping("/report")
    fun reportProgress(@RequestBody request: ReportProgressRequest): ApiResponse<String> {
        val userId = getCurrentUserId()
        logger.debug("[进度接口] 上报进度 userId={}, bookId={}, chapterIdx={}",
            userId, request.bookId, request.durChapterIndex)
        chapterService.reportProgress(userId, request)
        return ApiResponse.success("进度已保存")
    }

    /**
     * 查询阅读进度
     * 调用时机：打开书籍时，从服务端同步最新进度（与本地缓存取较新的）
     */
    @GetMapping("/{bookId}")
    fun getProgress(@PathVariable bookId: String): ApiResponse<ReadingProgressResponse> {
        val userId = getCurrentUserId()
        logger.debug("[进度接口] 查询进度 userId={}, bookId={}", userId, bookId)
        return ApiResponse.success(chapterService.getProgress(userId, bookId))
    }
}
