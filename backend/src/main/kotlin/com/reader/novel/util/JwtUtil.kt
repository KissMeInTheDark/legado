package com.reader.novel.util

import com.reader.novel.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * JWT 工具类
 * 负责生成和解析 JWT Token
 */
@Component
class JwtUtil(private val jwtProperties: JwtProperties) {

    private val logger = LoggerFactory.getLogger(JwtUtil::class.java)

    /** 生成签名 Key（需要至少32字节） */
    private val signingKey by lazy {
        val secretBytes = jwtProperties.secret.toByteArray(StandardCharsets.UTF_8)
        Keys.hmacShaKeyFor(secretBytes)
    }

    /**
     * 生成 JWT Token
     * @param userId 用户唯一ID
     * @return JWT Token 字符串
     */
    fun generateToken(userId: String): String {
        val now = Date()
        val expireMs = jwtProperties.expireHours * 3600 * 1000
        val expireAt = Date(now.time + expireMs)

        val token = Jwts.builder()
            .subject(userId)
            .issuedAt(now)
            .expiration(expireAt)
            .signWith(signingKey)
            .compact()

        logger.debug("[JWT] 生成Token userId={}, expireAt={}", userId, expireAt)
        return token
    }

    /**
     * 解析 JWT Token，获取 Claims
     * @return Claims 或 null（Token 无效时）
     */
    fun parseToken(token: String): Claims? {
        return try {
            Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            logger.warn("[JWT] Token 已过期")
            null
        } catch (e: Exception) {
            logger.warn("[JWT] Token 解析失败: {}", e.message)
            null
        }
    }

    /**
     * 从 Token 中提取用户ID
     */
    fun extractUserId(token: String): String? = parseToken(token)?.subject

    /**
     * 计算 Token 过期时间戳（毫秒）
     */
    fun getExpireAt(): Long = System.currentTimeMillis() + jwtProperties.expireHours * 3600 * 1000
}

/**
 * JWT 认证过滤器
 * 每次请求从 Authorization 头中提取 Token 并验证
 */
@Component
class JwtFilter(private val jwtUtil: JwtUtil) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(JwtFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 从请求头提取 Token
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
            val userId = jwtUtil.extractUserId(token)

            // Token 有效且当前请求没有已认证的用户
            if (userId != null && SecurityContextHolder.getContext().authentication == null) {
                logger.debug("[JWT过滤器] 认证用户 userId={}, path={}", userId, request.requestURI)

                // 设置认证信息到 SecurityContext
                val auth = UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }

        filterChain.doFilter(request, response)
    }
}

/**
 * 从 Spring Security Context 中获取当前登录用户ID的工具方法
 */
fun getCurrentUserId(): String {
    return SecurityContextHolder.getContext().authentication?.principal as? String
        ?: throw IllegalStateException("未登录用户")
}
