package com.reader.novel.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 统一 API 响应包装类
 *
 * 所有接口统一返回此格式，前端通过 code 判断成功/失败：
 *  - code=200: 成功
 *  - code≠200: 失败，message 为错误描述
 */
@JsonInclude(JsonInclude.Include.NON_NULL)  // 不序列化 null 字段
data class ApiResponse<T>(
    val code: Int = 200,
    val message: String = "success",
    val data: T? = null
) {
    companion object {
        /** 成功响应（带数据） */
        fun <T> success(data: T? = null) = ApiResponse(200, "success", data)

        /** 失败响应 */
        fun <T> error(code: Int, message: String) = ApiResponse<T>(code, message, null)
    }
}

// ==================== 请求 DTO ====================

/** 用户登录/注册请求 */
data class LoginRequest(
    val deviceId: String,           // 设备唯一ID
    val nickname: String? = null,   // 用户昵称（可选）
    val avatar: String? = null      // 头像URL（可选）
)

/** 刷新 Token 请求 */
data class RefreshTokenRequest(
    val token: String  // 旧 Token
)

/** 加入书架请求（搜索结果点击阅读后调用） */
data class AddBookRequest(
    val bookUrl: String,            // Legado 书籍 URL
    val bookName: String,           // 书名
    val author: String = "",        // 作者
    val coverUrl: String? = null,   // 封面 URL（来自搜索结果）
    val intro: String? = null,      // 简介
    val kind: String? = null,       // 分类
    val origin: String? = null,     // 书源 URL
    val originName: String? = null  // 书源名称
)

/** 批量获取章节预签名 URL 请求 */
data class BatchChapterUrlRequest(
    val chapterIdxList: List<Int>  // 需要获取 URL 的章节索引列表
)

/** 上报阅读进度请求 */
data class ReportProgressRequest(
    val bookId: String,                         // 书籍 ID
    val durChapterIndex: Int,                   // 当前章节索引
    val durChapterPos: Int = 0,                 // 章节内位置
    val durChapterTitle: String? = null,        // 章节标题
    val readTime: Long = 0L                     // 本次阅读时长（秒）
)

// ==================== 响应 DTO ====================

/** 用户登录响应 */
data class LoginResponse(
    val userId: String,
    val nickname: String,
    val avatar: String?,
    val token: String,
    val expiresAt: Long     // Token 过期时间戳（毫秒）
)

/** 书架书籍响应（含阅读进度） */
data class BookshelfItemResponse(
    val bookId: String,
    val bookName: String,
    val author: String,
    val coverOssUrl: String?,
    val intro: String?,
    val totalChapters: Int,
    val downloadStatus: Int,
    val readingProgress: ReadingProgressResponse?
)

/** 阅读进度响应 */
data class ReadingProgressResponse(
    val durChapterIndex: Int,
    val durChapterTitle: String?,
    val durChapterPos: Int,
    val lastReadAt: String,      // ISO 8601 格式时间字符串
    val totalReadTime: Long      // 累计阅读时长（秒）
)

/** 添加书籍响应 */
data class AddBookResponse(
    val bookId: String,
    val downloadStatus: Int,
    val message: String
)

/** 下载进度响应 */
data class DownloadStatusResponse(
    val bookId: String,
    val downloadStatus: Int,
    val totalChapters: Int,
    val doneChapters: Int,
    val percent: Double
)

/** 章节列表响应 */
data class ChapterListResponse(
    val bookId: String,
    val totalChapters: Int,
    val chapters: List<ChapterItemResponse>,
    val pagination: PaginationResponse
)

/** 章节列表项 */
data class ChapterItemResponse(
    val chapterIdx: Int,
    val title: String,
    val wordCount: Int,
    val isVip: Boolean,
    val ossKey: String?
)

/** 分页信息 */
data class PaginationResponse(
    val page: Int,
    val size: Int,
    val total: Long,
    val hasMore: Boolean
)

/** 章节预签名 URL 响应 */
data class ChapterUrlResponse(
    val chapterIdx: Int,
    val title: String,
    val ossPresignedUrl: String,
    val expireAt: Long,             // 预签名 URL 过期时间戳（毫秒）
    val wordCount: Int,
    val prevChapterIdx: Int?,       // 上一章索引（null 表示已是第一章）
    val nextChapterIdx: Int?        // 下一章索引（null 表示已是最后一章）
)

/** 批量章节 URL 响应 */
data class BatchChapterUrlResponse(
    val urls: List<ChapterUrlResponse>
)

/** 搜索结果书籍 */
data class SearchBookResult(
    val bookUrl: String,
    val bookName: String,
    val author: String,
    val coverUrl: String?,
    val intro: String?,
    val kind: String?,
    val latestChapterTitle: String?,
    val origin: String?,
    val originName: String?
)

/** 搜索响应 */
data class SearchResponse(
    val list: List<SearchBookResult>,
    val total: Int
)
