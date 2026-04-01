package com.reader.novel.service

import com.reader.novel.dto.*
import com.reader.novel.entity.ReadingProgress
import com.reader.novel.exception.BusinessException
import com.reader.novel.repository.ChapterRepository
import com.reader.novel.repository.ReadingProgressRepository
import com.reader.novel.util.OssUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * 章节与阅读进度服务
 *
 * 职责：
 *  1. 生成章节内容的阿里云 OSS 预签名访问 URL
 *  2. 批量生成多章节预签名 URL（用于预加载）
 *  3. 上报和查询用户阅读进度
 */
@Service
class ChapterService(
    private val chapterRepository: ChapterRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val ossUtil: OssUtil
) {
    private val logger = LoggerFactory.getLogger(ChapterService::class.java)

    /**
     * 获取单个章节的 OSS 预签名访问 URL
     *
     * 内部逻辑：
     *  1. 查询章节元数据（确认章节存在且已上传到 OSS）
     *  2. 生成 OSS 预签名 URL（有效期1小时）
     *  3. 返回当前章节信息及上下章索引
     *
     * @param bookId 书籍ID
     * @param chapterIdx 章节索引（从0开始）
     * @return 章节 URL 响应（含预签名 URL 和上下章信息）
     */
    fun getChapterUrl(bookId: String, chapterIdx: Int): ChapterUrlResponse {
        logger.info("[章节] 获取章节URL bookId={}, chapterIdx={}", bookId, chapterIdx)

        val chapter = chapterRepository.findByBookIdAndChapterIdx(bookId, chapterIdx)
            .orElseThrow { BusinessException(1003, "章节不存在: bookId=$bookId, idx=$chapterIdx") }

        val ossKey = chapter.ossKey
            ?: throw BusinessException(1002, "章节正在下载中，请稍后再试")

        // 生成预签名 URL（有效期由配置决定，默认1小时）
        val presignedUrl = ossUtil.generatePresignedUrl(ossKey)
        val expireAt = System.currentTimeMillis() + 3600_000L  // 毫秒

        // 查询上下章索引
        val totalChapters = chapterRepository.countByBookId(bookId).toInt()
        val prevIdx = if (chapterIdx > 0) chapterIdx - 1 else null
        val nextIdx = if (chapterIdx < totalChapters - 1) chapterIdx + 1 else null

        return ChapterUrlResponse(
            chapterIdx = chapter.chapterIdx,
            title = chapter.title,
            ossPresignedUrl = presignedUrl,
            expireAt = expireAt,
            wordCount = chapter.wordCount,
            prevChapterIdx = prevIdx,
            nextChapterIdx = nextIdx
        )
    }

    /**
     * 批量获取多个章节的 OSS 预签名 URL（用于预加载相邻章节）
     *
     * @param bookId 书籍ID
     * @param chapterIdxList 章节索引列表
     * @return 批量 URL 响应
     */
    fun getBatchChapterUrls(bookId: String, chapterIdxList: List<Int>): BatchChapterUrlResponse {
        logger.info("[章节] 批量获取章节URL bookId={}, idxList={}", bookId, chapterIdxList)

        val chapters = chapterRepository.findByBookIdAndChapterIdxIn(bookId, chapterIdxList)
        val totalChapters = chapterRepository.countByBookId(bookId).toInt()

        val urls = chapters.mapNotNull { chapter ->
            val ossKey = chapter.ossKey ?: return@mapNotNull null
            try {
                val presignedUrl = ossUtil.generatePresignedUrl(ossKey)
                val expireAt = System.currentTimeMillis() + 3600_000L
                val prevIdx = if (chapter.chapterIdx > 0) chapter.chapterIdx - 1 else null
                val nextIdx = if (chapter.chapterIdx < totalChapters - 1) chapter.chapterIdx + 1 else null
                ChapterUrlResponse(
                    chapterIdx = chapter.chapterIdx,
                    title = chapter.title,
                    ossPresignedUrl = presignedUrl,
                    expireAt = expireAt,
                    wordCount = chapter.wordCount,
                    prevChapterIdx = prevIdx,
                    nextChapterIdx = nextIdx
                )
            } catch (e: Exception) {
                logger.warn("[章节] 生成预签名URL失败 bookId={}, idx={}, error={}", bookId, chapter.chapterIdx, e.message)
                null
            }
        }

        return BatchChapterUrlResponse(urls = urls)
    }

    /**
     * 上报阅读进度
     *
     * 策略：如果已有进度记录则更新，否则创建新记录。
     * 累计阅读时长叠加存储。
     *
     * @param userId 当前用户ID
     * @param request 阅读进度上报请求
     */
    @Transactional
    fun reportProgress(userId: String, request: ReportProgressRequest) {
        logger.info("[进度] 上报阅读进度 userId={}, bookId={}, chapterIdx={}",
            userId, request.bookId, request.durChapterIndex)

        val existing = readingProgressRepository.findByUserIdAndBookId(userId, request.bookId)

        if (existing.isPresent) {
            // 更新已有进度（取最新章节位置）
            val progress = existing.get()
            progress.durChapterIndex = request.durChapterIndex
            progress.durChapterPos = request.durChapterPos
            request.durChapterTitle?.let { progress.durChapterTitle = it }
            progress.lastReadAt = LocalDateTime.now()
            progress.totalReadTime += request.readTime
            progress.updatedAt = LocalDateTime.now()
            readingProgressRepository.save(progress)
        } else {
            // 创建新进度记录
            val progress = ReadingProgress(
                userId = userId,
                bookId = request.bookId,
                durChapterIndex = request.durChapterIndex,
                durChapterPos = request.durChapterPos,
                durChapterTitle = request.durChapterTitle,
                totalReadTime = request.readTime
            )
            readingProgressRepository.save(progress)
        }
    }

    /**
     * 查询阅读进度
     *
     * @param userId 当前用户ID
     * @param bookId 书籍ID
     * @return 阅读进度（不存在时返回初始进度）
     */
    fun getProgress(userId: String, bookId: String): ReadingProgressResponse {
        logger.debug("[进度] 查询阅读进度 userId={}, bookId={}", userId, bookId)

        val progress = readingProgressRepository.findByUserIdAndBookId(userId, bookId).orElse(null)
            ?: return ReadingProgressResponse(
                durChapterIndex = 0,
                durChapterTitle = null,
                durChapterPos = 0,
                lastReadAt = LocalDateTime.now().toString(),
                totalReadTime = 0L
            )

        return ReadingProgressResponse(
            durChapterIndex = progress.durChapterIndex,
            durChapterTitle = progress.durChapterTitle,
            durChapterPos = progress.durChapterPos,
            lastReadAt = progress.lastReadAt.toString(),
            totalReadTime = progress.totalReadTime
        )
    }
}
