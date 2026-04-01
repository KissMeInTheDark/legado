package com.reader.novel.repository

import com.reader.novel.entity.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

/** 用户数据访问接口 */
@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUserId(userId: String): Optional<User>
    fun findByDeviceId(deviceId: String): Optional<User>
}

/** 书架数据访问接口 */
@Repository
interface BookshelfRepository : JpaRepository<Bookshelf, Long> {
    fun findByUserIdOrderBySortOrderAsc(userId: String): List<Bookshelf>
    fun findByUserIdAndBookId(userId: String, bookId: String): Optional<Bookshelf>
    fun existsByUserIdAndBookId(userId: String, bookId: String): Boolean
    fun deleteByUserIdAndBookId(userId: String, bookId: String)
}

/** 章节元数据数据访问接口 */
@Repository
interface ChapterRepository : JpaRepository<Chapter, Long> {
    fun findByBookIdOrderByChapterIdxAsc(bookId: String): List<Chapter>
    fun findByBookId(bookId: String, pageable: Pageable): Page<Chapter>
    fun findByBookIdAndChapterIdx(bookId: String, chapterIdx: Int): Optional<Chapter>
    fun countByBookId(bookId: String): Long

    @Query("SELECT c FROM Chapter c WHERE c.bookId = :bookId AND c.chapterIdx IN :idxList ORDER BY c.chapterIdx ASC")
    fun findByBookIdAndChapterIdxIn(bookId: String, idxList: List<Int>): List<Chapter>

    /** 批量保存章节元数据（使用 saveAll） */
    @Modifying
    @Query("DELETE FROM Chapter c WHERE c.bookId = :bookId")
    fun deleteAllByBookId(bookId: String)
}

/** 阅读进度数据访问接口 */
@Repository
interface ReadingProgressRepository : JpaRepository<ReadingProgress, Long> {
    fun findByUserIdAndBookId(userId: String, bookId: String): Optional<ReadingProgress>
    fun findByUserIdOrderByLastReadAtDesc(userId: String): List<ReadingProgress>
}

/** 下载任务数据访问接口 */
@Repository
interface DownloadTaskRepository : JpaRepository<DownloadTask, Long> {
    fun findByBookId(bookId: String): Optional<DownloadTask>
    fun findByStatus(status: Int): List<DownloadTask>
    fun findByBookIdOrderByCreatedAtDesc(bookId: String): List<DownloadTask>
}
