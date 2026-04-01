package com.reader.novel.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Legado 服务端配置属性
 * 通过 application.yml 的 legado.* 注入
 */
@ConfigurationProperties(prefix = "legado")
data class LegadoProperties(
    /** Legado HTTP 服务地址（如 http://legado-server:1234） */
    val baseUrl: String = "http://localhost:1234",
    /** Legado WebSocket 地址（如 ws://legado-server:1235） */
    val wsUrl: String = "ws://localhost:1235",
    /** Legado 认证用户名（未配置时为空，不发送 Basic Auth 头） */
    val username: String = "",
    /** Legado 认证密码 */
    val password: String = "",
    /** 连接超时（毫秒） */
    val connectTimeout: Long = 10_000L,
    /** 读取超时（毫秒） */
    val readTimeout: Long = 60_000L,
    /** 搜索超时（秒） */
    val searchTimeout: Long = 15L
)

/**
 * 阿里云 OSS 配置属性
 * 通过 application.yml 的 aliyun.oss.* 注入
 */
@ConfigurationProperties(prefix = "aliyun.oss")
data class OssProperties(
    /** OSS Endpoint（如 oss-cn-hangzhou.aliyuncs.com） */
    val endpoint: String = "",
    /** OSS AccessKey ID */
    val accessKeyId: String = "",
    /** OSS AccessKey Secret */
    val accessKeySecret: String = "",
    /** OSS Bucket 名称 */
    val bucketName: String = "",
    /** CDN 加速域名（可选，为空则直接使用 OSS URL） */
    val cdnDomain: String = "",
    /** 预签名 URL 有效期（秒） */
    val presignExpires: Long = 3600L
)

/**
 * JWT 配置属性
 */
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    /** JWT 签名密钥（生产环境必须修改） */
    val secret: String = "default-secret-please-change-in-production",
    /** Token 有效期（小时） */
    val expireHours: Long = 720L
)

/**
 * 下载配置属性
 */
@ConfigurationProperties(prefix = "download")
data class DownloadProperties(
    /** 章节下载并发数（避免对 Legado 和 OSS 造成过大压力） */
    val concurrency: Int = 3
)

/**
 * 统一注册所有配置属性类
 */
@Configuration
@EnableConfigurationProperties(
    LegadoProperties::class,
    OssProperties::class,
    JwtProperties::class,
    DownloadProperties::class
)
class AppConfig
