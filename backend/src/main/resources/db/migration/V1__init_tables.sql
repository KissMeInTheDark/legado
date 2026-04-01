-- ============================================================
-- Flyway 数据库迁移脚本 V1 - 初始化所有表结构
-- 创建时间：2026-04-01
-- 说明：小说阅读服务的核心数据库表
-- ============================================================

-- 1. 用户表：存储所有用户信息（不使用 Legado 的用户体系）
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id     VARCHAR(64)  NOT NULL UNIQUE COMMENT '用户唯一标识（UUID生成）',
    nickname    VARCHAR(64)  NOT NULL DEFAULT '读者' COMMENT '用户昵称',
    avatar      VARCHAR(512) COMMENT '头像URL（可选）',
    device_id   VARCHAR(128) COMMENT '设备ID（用于设备登录）',
    token       VARCHAR(512) NOT NULL COMMENT '最新JWT Token（用于追踪活跃会话）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- 2. 书架表：每个用户独立的书架，不依赖 Legado 书架
CREATE TABLE IF NOT EXISTS t_bookshelf (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id         VARCHAR(64)  NOT NULL COMMENT '所属用户ID',
    book_id         VARCHAR(32)  NOT NULL COMMENT '书籍唯一ID（bookUrl的MD5）',
    book_url        TEXT         NOT NULL COMMENT 'Legado 原始书籍URL（用于调用Legado API）',
    book_name       VARCHAR(256) NOT NULL COMMENT '书名',
    author          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '作者',
    cover_oss_url   VARCHAR(512) COMMENT 'OSS封面图片完整URL（下载后上传到OSS）',
    intro           TEXT COMMENT '书籍简介',
    origin          VARCHAR(512) COMMENT '书源URL（Legado书源地址）',
    origin_name     VARCHAR(128) COMMENT '书源名称',
    total_chapters  INT          NOT NULL DEFAULT 0 COMMENT '总章节数（下载完成后更新）',
    download_status TINYINT      NOT NULL DEFAULT 0 COMMENT '下载状态：0=待处理 1=下载中 2=已完成 3=下载失败',
    sort_order      INT          NOT NULL DEFAULT 0 COMMENT '书架排序（越小越靠前）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入书架时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    UNIQUE KEY uk_user_book (user_id, book_id),
    INDEX idx_user_id (user_id),
    INDEX idx_download_status (download_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户书架表';

-- 3. 章节元数据表：存储每本书所有章节的基本信息
CREATE TABLE IF NOT EXISTS t_chapter (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    book_id     VARCHAR(32)  NOT NULL COMMENT '所属书籍ID',
    chapter_idx INT          NOT NULL COMMENT '章节索引（从0开始，与Legado的index对应）',
    title       VARCHAR(512) NOT NULL COMMENT '章节标题',
    chapter_url TEXT COMMENT '原始章节URL（来自Legado）',
    oss_key     VARCHAR(512) COMMENT 'OSS对象Key（如 books/{bookId}/{idx}.txt）',
    word_count  INT          NOT NULL DEFAULT 0 COMMENT '正文字数（上传时统计）',
    is_vip      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否VIP付费章节',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_book_chapter (book_id, chapter_idx),
    INDEX idx_book_id (book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书籍章节元数据表';

-- 4. 阅读进度表：每个用户对每本书的阅读进度（独立管理，不依赖Legado）
CREATE TABLE IF NOT EXISTS t_reading_progress (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id             VARCHAR(64)  NOT NULL COMMENT '所属用户ID',
    book_id             VARCHAR(32)  NOT NULL COMMENT '所属书籍ID',
    dur_chapter_index   INT          NOT NULL DEFAULT 0 COMMENT '当前阅读章节索引',
    dur_chapter_pos     INT          NOT NULL DEFAULT 0 COMMENT '章节内阅读位置（字符偏移量）',
    dur_chapter_title   VARCHAR(512) COMMENT '当前阅读章节标题（冗余存储，方便展示）',
    last_read_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后阅读时间',
    total_read_time     BIGINT       NOT NULL DEFAULT 0 COMMENT '累计阅读时长（秒）',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_book (user_id, book_id),
    INDEX idx_user_id (user_id),
    INDEX idx_last_read_at (last_read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户阅读进度表';

-- 5. 书籍下载任务表：跟踪异步下载任务的进度
CREATE TABLE IF NOT EXISTS t_download_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    book_id         VARCHAR(32)  NOT NULL COMMENT '书籍ID',
    user_id         VARCHAR(64)  NOT NULL COMMENT '触发下载的用户ID',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '任务状态：0=等待中 1=进行中 2=已完成 3=失败',
    total_chapters  INT          NOT NULL DEFAULT 0 COMMENT '总章节数（从Legado获取后更新）',
    done_chapters   INT          NOT NULL DEFAULT 0 COMMENT '已成功下载并上传OSS的章节数',
    error_msg       TEXT COMMENT '失败时的错误信息（便于排查）',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    INDEX idx_book_id (book_id),
    INDEX idx_status (status),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书籍章节下载任务表';
