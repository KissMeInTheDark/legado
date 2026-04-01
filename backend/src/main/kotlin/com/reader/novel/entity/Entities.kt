package com.reader.novel.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * 用户实体
 * 对应数据库表 t_user
 * 不使用 Legado 的用户体系，独立管理
 */
@Entity
@Table(name = "t_user")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 用户唯一ID（UUID，对外使用） */
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: String = "",

    /** 用户昵称 */
    @Column(nullable = false)
    var nickname: String = "读者",

    /** 头像URL */
    @Column
    var avatar: String? = null,

    /** 设备ID（用于同一设备自动登录） */
    @Column(name = "device_id")
    var deviceId: String? = null,

    /** 最新 JWT Token（用于追踪活跃会话） */
    @Column(nullable = false, length = 512)
    var token: String = "",

    /** 注册时间 */
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 书架实体
 * 对应数据库表 t_bookshelf
 * 每个用户独立的书架，书籍信息独立存储
 */
@Entity
@Table(name = "t_bookshelf")
data class Bookshelf(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 所属用户ID */
    @Column(name = "user_id", nullable = false)
    val userId: String = "",

    /** 书籍唯一ID（bookUrl 的 MD5 哈希） */
    @Column(name = "book_id", nullable = false, length = 32)
    val bookId: String = "",

    /** Legado 原始书籍 URL（调用 Legado API 时使用） */
    @Column(name = "book_url", nullable = false, columnDefinition = "TEXT")
    val bookUrl: String = "",

    /** 书名 */
    @Column(name = "book_name", nullable = false)
    var bookName: String = "",

    /** 作者 */
    @Column(nullable = false)
    var author: String = "",

    /** OSS 封面图片 URL（下载后上传到 OSS 的完整 URL） */
    @Column(name = "cover_oss_url")
    var coverOssUrl: String? = null,

    /** 书籍简介 */
    @Column(columnDefinition = "TEXT")
    var intro: String? = null,

    /** 书源 URL */
    @Column
    var origin: String? = null,

    /** 书源名称 */
    @Column(name = "origin_name")
    var originName: String? = null,

    /** 总章节数（下载完成后更新） */
    @Column(name = "total_chapters", nullable = false)
    var totalChapters: Int = 0,

    /**
     * 下载状态：
     *  0 = 待处理（刚加入书架）
     *  1 = 下载中（正在从 Legado 抓取章节并上传 OSS）
     *  2 = 已完成
     *  3 = 下载失败
     */
    @Column(name = "download_status", nullable = false)
    var downloadStatus: Int = 0,

    /** 书架排序 */
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 章节元数据实体
 * 对应数据库表 t_chapter
 * 存储每本书所有章节的基本信息，内容存储在 OSS
 */
@Entity
@Table(name = "t_chapter")
data class Chapter(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 所属书籍 ID */
    @Column(name = "book_id", nullable = false, length = 32)
    val bookId: String = "",

    /** 章节索引（从0开始，与 Legado 的 index 参数对应） */
    @Column(name = "chapter_idx", nullable = false)
    val chapterIdx: Int = 0,

    /** 章节标题 */
    @Column(nullable = false, length = 512)
    var title: String = "",

    /** 原始章节 URL（来自 Legado，用于回源重新下载） */
    @Column(name = "chapter_url", columnDefinition = "TEXT")
    var chapterUrl: String? = null,

    /** OSS 对象 Key（如 books/{bookId}/{idx}.txt） */
    @Column(name = "oss_key")
    var ossKey: String? = null,

    /** 正文字数 */
    @Column(name = "word_count", nullable = false)
    var wordCount: Int = 0,

    /** 是否 VIP 付费章节 */
    @Column(name = "is_vip", nullable = false)
    var isVip: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 阅读进度实体
 * 对应数据库表 t_reading_progress
 * 每个用户对每本书的阅读位置，独立于 Legado 管理
 */
@Entity
@Table(name = "t_reading_progress")
data class ReadingProgress(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    val userId: String = "",

    /** 所属书籍 ID */
    @Column(name = "book_id", nullable = false, length = 32)
    val bookId: String = "",

    /** 当前阅读章节索引 */
    @Column(name = "dur_chapter_index", nullable = false)
    var durChapterIndex: Int = 0,

    /** 章节内阅读位置（字符偏移量） */
    @Column(name = "dur_chapter_pos", nullable = false)
    var durChapterPos: Int = 0,

    /** 当前阅读章节标题（冗余存储，方便展示） */
    @Column(name = "dur_chapter_title", length = 512)
    var durChapterTitle: String? = null,

    /** 最后阅读时间 */
    @Column(name = "last_read_at", nullable = false)
    var lastReadAt: LocalDateTime = LocalDateTime.now(),

    /** 累计阅读时长（秒） */
    @Column(name = "total_read_time", nullable = false)
    var totalReadTime: Long = 0L,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 书籍下载任务实体
 * 对应数据库表 t_download_task
 * 跟踪后台异步下载任务进度
 */
@Entity
@Table(name = "t_download_task")
data class DownloadTask(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    /** 书籍 ID */
    @Column(name = "book_id", nullable = false, length = 32)
    val bookId: String = "",

    /** 触发下载的用户 ID */
    @Column(name = "user_id", nullable = false)
    val userId: String = "",

    /**
     * 任务状态：
     *  0 = 等待中
     *  1 = 进行中
     *  2 = 已完成
     *  3 = 失败
     */
    @Column(nullable = false)
    var status: Int = 0,

    /** 总章节数（从 Legado 获取章节列表后更新） */
    @Column(name = "total_chapters", nullable = false)
    var totalChapters: Int = 0,

    /** 已完成章节数（每成功上传一章更新一次） */
    @Column(name = "done_chapters", nullable = false)
    var doneChapters: Int = 0,

    /** 失败原因（便于排查问题） */
    @Column(name = "error_msg", columnDefinition = "TEXT")
    var errorMsg: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
