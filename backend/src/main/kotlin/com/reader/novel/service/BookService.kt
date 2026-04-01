package com.reader.novel.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.reader.novel.config.DownloadProperties
import com.reader.novel.dto.*
import com.reader.novel.entity.*
import com.reader.novel.exception.BusinessException
import com.reader.novel.repository.*
import com.reader.novel.util.CommonUtil
import com.reader.novel.util.LegadoClient
import com.reader.novel.util.OssUtil
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.util.concurrent.Semaphore

/**
 * 书籍服务
 *
 * 职责：
 *  1. 用户书架管理（增删查）
 *  2. 书籍章节列表查询
 *  3. 触发并管理书籍下载任务（异步，从 Legado 抓取后上传 OSS）
 */
@Service
class BookService(
    private val bookshelfRepository: BookshelfRepository,
    private val chapterRepository: ChapterRepository,
    private val downloadTaskRepository: DownloadTaskRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val legadoClient: LegadoClient,
    private val ossUtil: OssUtil,
    private val downloadProperties: DownloadProperties
) {
    private val logger = LoggerFactory.getLogger(BookService::class.java)
    private val json = ObjectMapper().registerKotlinModule()

    /**
     * 获取用户书架列表（含最新阅读进度）
     *
     * @param userId 当前登录用户ID
     * @return 书架书籍列表（按加入时间倒序）
     */
    fun getBookshelf(userId: String): List<BookshelfItemResponse> {
        logger.info("[书架] 获取书架 userId={}", userId)

        val books = bookshelfRepository.findByUserIdOrderBySortOrderAsc(userId)
        val progressMap = readingProgressRepository.findByUserIdOrderByLastReadAtDesc(userId)
            .associateBy { it.bookId }

        return books.map { book ->
            val progress = progressMap[book.bookId]
            BookshelfItemResponse(
                bookId = book.bookId,
                bookName = book.bookName,
                author = book.author,
                coverOssUrl = book.coverOssUrl,
                intro = book.intro,
                totalChapters = book.totalChapters,
                downloadStatus = book.downloadStatus,
                readingProgress = progress?.let {
                    ReadingProgressResponse(
                        durChapterIndex = it.durChapterIndex,
                        durChapterTitle = it.durChapterTitle,
                        durChapterPos = it.durChapterPos,
                        lastReadAt = it.lastReadAt.toString(),
                        totalReadTime = it.totalReadTime
                    )
                }
            )
        }
    }

    /**
     * 将书籍加入书架并触发异步下载
     *
     * 如果该书已在书架中（downloadStatus=2），直接返回已存在状态。
     * 否则创建书架记录并提交异步下载任务。
     *
     * @param userId 当前用户ID
     * @param request 书籍信息
     * @return 添加结果（含下载状态）
     */
    @Transactional
    fun addBook(userId: String, request: AddBookRequest): AddBookResponse {
        val bookId = CommonUtil.generateBookId(request.bookUrl)
        logger.info("[书架] 加入书架 userId={}, bookName={}, bookId={}", userId, request.bookName, bookId)

        // 检查是否已在书架
        val existing = bookshelfRepository.findByUserIdAndBookId(userId, bookId).orElse(null)
        if (existing != null) {
            logger.info("[书架] 书籍已在书架 bookId={}, status={}", bookId, existing.downloadStatus)
            return AddBookResponse(
                bookId = bookId,
                downloadStatus = existing.downloadStatus,
                message = if (existing.downloadStatus == 2) "书籍已在书架" else "正在后台下载中..."
            )
        }

        // 创建书架记录（初始状态：待处理）
        val bookshelf = Bookshelf(
            userId = userId,
            bookId = bookId,
            bookUrl = request.bookUrl,
            bookName = request.bookName,
            author = request.author,
            coverOssUrl = null,  // 下载任务完成后更新
            intro = request.intro,
            origin = request.origin,
            originName = request.originName,
            downloadStatus = 0  // 待处理
        )
        bookshelfRepository.save(bookshelf)

        // 创建下载任务记录
        val task = DownloadTask(
            bookId = bookId,
            userId = userId,
            status = 0
        )
        downloadTaskRepository.save(task)

        // 提交异步下载任务（不阻塞当前请求）
        triggerDownload(bookId, request.bookUrl, request.coverUrl, userId)

        logger.info("[书架] 已触发下载任务 bookId={}", bookId)
        return AddBookResponse(
            bookId = bookId,
            downloadStatus = 1,
            message = "书籍已加入书架，正在后台下载章节..."
        )
    }

    /**
     * 查询书籍下载进度
     *
     * @param bookId 书籍ID
     * @return 下载状态和进度
     */
    fun getDownloadStatus(bookId: String): DownloadStatusResponse {
        val task = downloadTaskRepository.findByBookId(bookId)
            .orElseThrow { BusinessException(404, "下载任务不存在") }

        val percent = if (task.totalChapters > 0) {
            (task.doneChapters.toDouble() / task.totalChapters) * 100
        } else 0.0

        return DownloadStatusResponse(
            bookId = bookId,
            downloadStatus = task.status,
            totalChapters = task.totalChapters,
            doneChapters = task.doneChapters,
            percent = String.format("%.1f", percent).toDouble()
        )
    }

    /**
     * 从书架移除书籍
     *
     * @param userId 当前用户ID
     * @param bookId 书籍ID
     */
    @Transactional
    fun removeFromBookshelf(userId: String, bookId: String) {
        logger.info("[书架] 移除书籍 userId={}, bookId={}", userId, bookId)
        if (!bookshelfRepository.existsByUserIdAndBookId(userId, bookId)) {
            throw BusinessException(404, "书架中不存在该书籍")
        }
        bookshelfRepository.deleteByUserIdAndBookId(userId, bookId)
    }

    /**
     * 获取书籍章节列表（分页）
     *
     * @param bookId 书籍ID
     * @param page 页码（从1开始）
     * @param size 每页条数
     * @return 章节列表（含分页信息）
     */
    fun getChapterList(bookId: String, page: Int, size: Int): ChapterListResponse {
        logger.info("[章节] 获取章节列表 bookId={}, page={}, size={}", bookId, page, size)

        val pageable = PageRequest.of(page - 1, size)
        val chapterPage = chapterRepository.findByBookId(bookId, pageable)
        val total = chapterRepository.countByBookId(bookId)

        val chapters = chapterPage.content.map { chapter ->
            ChapterItemResponse(
                chapterIdx = chapter.chapterIdx,
                title = chapter.title,
                wordCount = chapter.wordCount,
                isVip = chapter.isVip,
                ossKey = chapter.ossKey
            )
        }

        return ChapterListResponse(
            bookId = bookId,
            totalChapters = total.toInt(),
            chapters = chapters,
            pagination = PaginationResponse(
                page = page,
                size = size,
                total = total,
                hasMore = chapterPage.hasNext()
            )
        )
    }

    // ==================== 异步下载任务 ====================

    /**
     * 触发书籍下载（异步方法，不阻塞主线程）
     *
     * 流程：
     *  1. 将书籍保存到 Legado 服务端缓存
     *  2. 从 Legado 获取章节列表
     *  3. 批量保存章节元数据到数据库
     *  4. 逐章节下载内容并上传到 OSS（并发控制）
     *  5. 下载封面图并上传到 OSS
     *  6. 更新书架和任务状态
     *
     * @param bookId 书籍ID
     * @param bookUrl Legado 书籍 URL
     * @param coverUrl 封面原始 URL
     * @param userId 触发下载的用户ID
     */
    @Async("downloadTaskExecutor")
    fun triggerDownload(bookId: String, bookUrl: String, coverUrl: String?, userId: String) {
        logger.info("[下载任务] 开始执行 bookId={}, userId={}", bookId, userId)

        try {
            // 步骤1：更新任务状态为"进行中"
            updateTaskStatus(bookId, 1, null)
            updateBookDownloadStatus(userId, bookId, 1)

            // 步骤2：将书籍保存到 Legado 缓存（必须先保存才能获取章节）
            val bookJson = """{"bookUrl":"$bookUrl","name":"","author":"","origin":"","tocUrl":""}"""
            try {
                legadoClient.saveBook(bookJson)
                logger.info("[下载任务] 书籍已保存到Legado缓存 bookId={}", bookId)
            } catch (e: Exception) {
                logger.warn("[下载任务] 保存到Legado失败（可能已存在），继续下载 error={}", e.message)
            }

            // 步骤3：获取章节列表
            val chapterNodes = legadoClient.getChapterList(bookUrl)
            if (chapterNodes.isEmpty()) {
                throw RuntimeException("获取章节列表为空，bookUrl=$bookUrl")
            }
            logger.info("[下载任务] 获取到{}个章节 bookId={}", chapterNodes.size, bookId)

            // 步骤4：批量保存章节元数据
            val chapters = chapterNodes.mapIndexed { idx, node ->
                Chapter(
                    bookId = bookId,
                    chapterIdx = idx,
                    title = node.get("title")?.asText() ?: "第${idx + 1}章",
                    chapterUrl = node.get("url")?.asText(),
                    isVip = node.get("isVip")?.asBoolean() ?: false
                )
            }
            chapterRepository.saveAll(chapters)
            logger.info("[下载任务] 章节元数据保存完毕 bookId={}, count={}", bookId, chapters.size)

            // 更新任务总章节数
            updateTaskTotalChapters(bookId, chapters.size)
            updateBookTotalChapters(userId, bookId, chapters.size)

            // 步骤5：逐章节下载内容并上传 OSS（使用信号量控制并发）
            val semaphore = Semaphore(downloadProperties.concurrency)
            var doneCount = 0

            chapters.forEach { chapter ->
                semaphore.acquire()
                try {
                    val content = legadoClient.getBookContent(bookUrl, chapter.chapterIdx)
                    if (content.isNotBlank()) {
                        val ossKey = ossUtil.uploadChapterContent(bookId, chapter.chapterIdx, content)
                        // 更新章节的 OSS Key 和字数
                        chapter.ossKey = ossKey
                        chapter.wordCount = CommonUtil.countWords(content)
                        chapter.updatedAt = LocalDateTime.now()
                        chapterRepository.save(chapter)
                    } else {
                        logger.warn("[下载任务] 章节内容为空 bookId={}, idx={}", bookId, chapter.chapterIdx)
                    }

                    doneCount++
                    updateTaskDoneChapters(bookId, doneCount)
                    logger.debug("[下载任务] 章节完成 bookId={}, idx={}, done={}/{}", bookId, chapter.chapterIdx, doneCount, chapters.size)
                } catch (e: Exception) {
                    logger.warn("[下载任务] 章节下载失败 bookId={}, idx={}, error={}", bookId, chapter.chapterIdx, e.message)
                    doneCount++
                    updateTaskDoneChapters(bookId, doneCount)
                } finally {
                    semaphore.release()
                }
            }

            // 步骤6：下载并上传封面图
            if (!coverUrl.isNullOrBlank()) {
                val coverOssKey = ossUtil.buildCoverKey(bookId)
                val uploadedKey = ossUtil.uploadImageFromUrl(coverUrl, coverOssKey)
                if (uploadedKey != null) {
                    val coverOssUrl = ossUtil.getPublicUrl(coverOssKey)
                    updateBookCoverUrl(userId, bookId, coverOssUrl)
                    logger.info("[下载任务] 封面上传成功 bookId={}, coverOssUrl={}", bookId, coverOssUrl)
                }
            }

            // 步骤7：更新任务和书架状态为"完成"
            updateTaskStatus(bookId, 2, null)
            updateBookDownloadStatus(userId, bookId, 2)
            logger.info("[下载任务] 下载完成 bookId={}, total={}", bookId, chapters.size)

        } catch (e: Exception) {
            logger.error("[下载任务] 下载失败 bookId={}", bookId, e)
            updateTaskStatus(bookId, 3, e.message)
            updateBookDownloadStatus(userId, bookId, 3)
        }
    }

    // ==================== 状态更新辅助方法 ====================

    @Transactional
    fun updateTaskStatus(bookId: String, status: Int, errorMsg: String?) {
        downloadTaskRepository.findByBookId(bookId).ifPresent { task ->
            task.status = status
            task.errorMsg = errorMsg
            task.updatedAt = LocalDateTime.now()
            downloadTaskRepository.save(task)
        }
    }

    @Transactional
    fun updateTaskTotalChapters(bookId: String, total: Int) {
        downloadTaskRepository.findByBookId(bookId).ifPresent { task ->
            task.totalChapters = total
            task.updatedAt = LocalDateTime.now()
            downloadTaskRepository.save(task)
        }
    }

    @Transactional
    fun updateTaskDoneChapters(bookId: String, done: Int) {
        downloadTaskRepository.findByBookId(bookId).ifPresent { task ->
            task.doneChapters = done
            task.updatedAt = LocalDateTime.now()
            downloadTaskRepository.save(task)
        }
    }

    @Transactional
    fun updateBookDownloadStatus(userId: String, bookId: String, status: Int) {
        bookshelfRepository.findByUserIdAndBookId(userId, bookId).ifPresent { book ->
            book.downloadStatus = status
            book.updatedAt = LocalDateTime.now()
            bookshelfRepository.save(book)
        }
    }

    @Transactional
    fun updateBookTotalChapters(userId: String, bookId: String, total: Int) {
        bookshelfRepository.findByUserIdAndBookId(userId, bookId).ifPresent { book ->
            book.totalChapters = total
            book.updatedAt = LocalDateTime.now()
            bookshelfRepository.save(book)
        }
    }

    @Transactional
    fun updateBookCoverUrl(userId: String, bookId: String, coverOssUrl: String) {
        bookshelfRepository.findByUserIdAndBookId(userId, bookId).ifPresent { book ->
            book.coverOssUrl = coverOssUrl
            book.updatedAt = LocalDateTime.now()
            bookshelfRepository.save(book)
        }
    }
}
