package com.reader.novel.service

import com.reader.novel.config.JwtProperties
import com.reader.novel.dto.*
import com.reader.novel.entity.User
import com.reader.novel.exception.BusinessException
import com.reader.novel.repository.UserRepository
import com.reader.novel.util.JwtUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * 用户服务
 *
 * 策略：以设备ID为主键进行"登录即注册"，无需密码。
 * 相同设备ID的用户每次调用都刷新 Token，保持会话活跃。
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val jwtUtil: JwtUtil,
    private val jwtProperties: JwtProperties
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    /**
     * 用户登录/注册（设备一键登录）
     *
     * 逻辑：
     *  1. 根据设备ID查找用户
     *  2. 如果不存在，创建新用户
     *  3. 生成新 JWT Token 并返回
     *
     * @param request 登录请求（包含设备ID）
     * @return 登录响应（含 Token）
     */
    @Transactional
    fun loginOrRegister(request: LoginRequest): LoginResponse {
        logger.info("[用户服务] 用户登录 deviceId={}", request.deviceId)

        val user = userRepository.findByDeviceId(request.deviceId).orElse(null)
            ?: createNewUser(request)

        // 更新用户昵称和头像（如果有提供）
        request.nickname?.let { user.nickname = it }
        request.avatar?.let { user.avatar = it }

        // 生成新 Token
        val token = jwtUtil.generateToken(user.userId)
        user.token = token
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)

        logger.info("[用户服务] 登录成功 userId={}, nickname={}", user.userId, user.nickname)
        return LoginResponse(
            userId = user.userId,
            nickname = user.nickname,
            avatar = user.avatar,
            token = token,
            expiresAt = jwtUtil.getExpireAt()
        )
    }

    /**
     * 刷新 JWT Token
     *
     * @param request 包含旧 Token 的请求
     * @return 新的 Token 信息
     */
    @Transactional
    fun refreshToken(request: RefreshTokenRequest): LoginResponse {
        logger.info("[用户服务] 刷新Token")

        // 注意：parseToken 对过期 Token 返回 null，此处允许过期的 Token 刷新（在一定窗口期内）
        // 实际项目中可根据需求调整刷新策略
        val userId = jwtUtil.extractUserId(request.token)
            ?: throw BusinessException(401, "无效的Token，请重新登录")

        val user = userRepository.findByUserId(userId)
            .orElseThrow { BusinessException(401, "用户不存在") }

        val newToken = jwtUtil.generateToken(user.userId)
        user.token = newToken
        user.updatedAt = LocalDateTime.now()
        userRepository.save(user)

        logger.info("[用户服务] Token刷新成功 userId={}", userId)
        return LoginResponse(
            userId = user.userId,
            nickname = user.nickname,
            avatar = user.avatar,
            token = newToken,
            expiresAt = jwtUtil.getExpireAt()
        )
    }

    /** 创建新用户 */
    private fun createNewUser(request: LoginRequest): User {
        val userId = "usr_${UUID.randomUUID().toString().replace("-", "")}"
        val user = User(
            userId = userId,
            nickname = request.nickname ?: "读者",
            avatar = request.avatar,
            deviceId = request.deviceId,
            token = ""  // 稍后生成
        )
        logger.info("[用户服务] 创建新用户 userId={}, deviceId={}", userId, request.deviceId)
        return userRepository.save(user)
    }
}
