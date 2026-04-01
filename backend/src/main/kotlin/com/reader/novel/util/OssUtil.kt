package com.reader.novel.util

import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.model.GeneratePresignedUrlRequest
import com.aliyun.oss.model.ObjectMetadata
import com.reader.novel.config.OssProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.util.*

/**
 * 阿里云 OSS 工具类
 *
 * 职责：
 *  1. 上传文本内容（章节正文）
 *  2. 上传图片（封面）
 *  3. 生成预签名访问 URL（供前端直接下载章节内容）
 *
 * OSS 存储结构：
 *  books/{bookId}/cover.jpg    - 封面图片
 *  books/{bookId}/{idx}.txt    - 第 idx 章内容（纯文本 UTF-8）
 */
@Component
class OssUtil(private val ossProperties: OssProperties) {

    private val logger = LoggerFactory.getLogger(OssUtil::class.java)

    /** 懒加载 OSS 客户端（避免未配置时报错） */
    private val ossClient: OSS by lazy {
        logger.info("[OSS] 初始化客户端 endpoint={}, bucket={}", ossProperties.endpoint, ossProperties.bucketName)
        OSSClientBuilder().build(
            ossProperties.endpoint,
            ossProperties.accessKeyId,
            ossProperties.accessKeySecret
        )
    }

    /**
     * 上传章节文本内容到 OSS
     *
     * @param bookId 书籍 ID
     * @param chapterIdx 章节索引
     * @param content 章节正文文本
     * @return OSS 对象 Key
     */
    fun uploadChapterContent(bookId: String, chapterIdx: Int, content: String): String {
        val key = buildChapterKey(bookId, chapterIdx)
        logger.info("[OSS] 上传章节 bookId={}, chapterIdx={}, key={}, size={}字", bookId, chapterIdx, key, content.length)

        val bytes = content.toByteArray(Charsets.UTF_8)
        val meta = ObjectMetadata().apply {
            contentType = "text/plain;charset=UTF-8"
            contentLength = bytes.size.toLong()
        }

        ossClient.putObject(ossProperties.bucketName, key, ByteArrayInputStream(bytes), meta)
        logger.debug("[OSS] 章节上传成功 key={}", key)
        return key
    }

    /**
     * 从 URL 下载图片并上传到 OSS（封面图片处理）
     *
     * @param imageUrl 图片原始 URL
     * @param ossKey OSS 存储路径
     * @return 实际存储的 OSS Key（成功时），失败时返回 null
     */
    fun uploadImageFromUrl(imageUrl: String, ossKey: String): String? {
        return try {
            logger.info("[OSS] 下载并上传图片 imageUrl={}, ossKey={}", imageUrl, ossKey)
            val url = URL(imageUrl)
            val connection = url.openConnection().apply {
                connectTimeout = 10_000
                readTimeout = 30_000
            }

            connection.getInputStream().use { inputStream ->
                val contentType = connection.contentType ?: "image/jpeg"
                uploadStream(ossKey, inputStream, contentType)
            }
            logger.info("[OSS] 图片上传成功 ossKey={}", ossKey)
            ossKey
        } catch (e: Exception) {
            logger.warn("[OSS] 图片上传失败 imageUrl={}, error={}", imageUrl, e.message)
            null
        }
    }

    /**
     * 上传输入流到 OSS
     *
     * @param key OSS 对象 Key
     * @param inputStream 输入流
     * @param contentType MIME 类型
     */
    fun uploadStream(key: String, inputStream: InputStream, contentType: String = "application/octet-stream") {
        val meta = ObjectMetadata().apply {
            this.contentType = contentType
        }
        ossClient.putObject(ossProperties.bucketName, key, inputStream, meta)
    }

    /**
     * 生成章节内容预签名访问 URL
     * 前端使用此 URL 直接从 OSS 下载章节内容，有效期1小时
     *
     * @param ossKey OSS 对象 Key
     * @return 预签名 URL（字符串）
     */
    fun generatePresignedUrl(ossKey: String): String {
        val expireAt = Date(System.currentTimeMillis() + ossProperties.presignExpires * 1000L)
        val request = GeneratePresignedUrlRequest(ossProperties.bucketName, ossKey).apply {
            expiration = expireAt
        }
        val url = ossClient.generatePresignedUrl(request).toString()
        logger.debug("[OSS] 生成预签名URL key={}, expireAt={}", ossKey, expireAt)

        // 如果配置了 CDN 域名，替换 URL 中的 OSS 域名为 CDN 域名
        return if (ossProperties.cdnDomain.isNotBlank()) {
            replaceToCdnDomain(url)
        } else {
            url
        }
    }

    /**
     * 获取章节的公开访问 URL（用于封面图等不需要预签名的场景）
     */
    fun getPublicUrl(ossKey: String): String {
        return if (ossProperties.cdnDomain.isNotBlank()) {
            "${ossProperties.cdnDomain.trimEnd('/')}/$ossKey"
        } else {
            "https://${ossProperties.bucketName}.${ossProperties.endpoint}/$ossKey"
        }
    }

    /** 删除 OSS 对象 */
    fun delete(ossKey: String) {
        try {
            ossClient.deleteObject(ossProperties.bucketName, ossKey)
            logger.info("[OSS] 删除对象 key={}", ossKey)
        } catch (e: Exception) {
            logger.warn("[OSS] 删除对象失败 key={}, error={}", ossKey, e.message)
        }
    }

    /** 构建章节 OSS Key */
    fun buildChapterKey(bookId: String, chapterIdx: Int) = "books/$bookId/$chapterIdx.txt"

    /** 构建封面 OSS Key */
    fun buildCoverKey(bookId: String) = "books/$bookId/cover.jpg"

    /** 将 OSS URL 的域名替换为 CDN 域名 */
    private fun replaceToCdnDomain(ossUrl: String): String {
        return try {
            val url = URL(ossUrl)
            val cdnBase = ossProperties.cdnDomain.trimEnd('/')
            "$cdnBase${url.path}${if (url.query != null) "?${url.query}" else ""}"
        } catch (e: Exception) {
            logger.warn("[OSS] CDN域名替换失败 ossUrl={}", ossUrl)
            ossUrl
        }
    }
}
