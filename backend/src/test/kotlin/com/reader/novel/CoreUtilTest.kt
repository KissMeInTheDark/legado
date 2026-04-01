package com.reader.novel

import com.reader.novel.util.CommonUtil
import com.reader.novel.util.JwtUtil
import com.reader.novel.config.JwtProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 核心工具类单元测试
 * 不依赖 Spring Context，快速验证核心逻辑
 */
class CoreUtilTest {

    /**
     * 测试书籍 ID 生成的确定性（相同输入必须输出相同 MD5）
     */
    @Test
    fun `书籍ID生成应该具有确定性`() {
        val bookUrl = "http://www.biquge.com/book/12345"
        val bookId1 = CommonUtil.generateBookId(bookUrl)
        val bookId2 = CommonUtil.generateBookId(bookUrl)
        assertEquals(bookId1, bookId2, "相同 bookUrl 应该生成相同 bookId")
        assertEquals(32, bookId1.length, "MD5 应该是32位")
    }

    /**
     * 测试不同 URL 生成不同 ID
     */
    @Test
    fun `不同bookUrl应该生成不同bookId`() {
        val id1 = CommonUtil.generateBookId("http://example.com/book/1")
        val id2 = CommonUtil.generateBookId("http://example.com/book/2")
        assertNotEquals(id1, id2, "不同 URL 应该生成不同 bookId")
    }

    /**
     * 测试字数统计（过滤空白字符）
     */
    @Test
    fun `字数统计应该过滤空白字符`() {
        val content = "这是一段文字。\n\n这是第二段。"
        val count = CommonUtil.countWords(content)
        // 实际字符（非空白）：这是一段文字。这是第二段。= 12
        assertEquals(12, count)
    }

    /**
     * 测试 JWT Token 生成和解析
     */
    @Test
    fun `JWT Token应该能正确生成和解析用户ID`() {
        val jwtProperties = JwtProperties(
            secret = "test-secret-key-must-be-at-least-32-bytes",
            expireHours = 24L
        )
        val jwtUtil = JwtUtil(jwtProperties)

        val userId = "usr_testuser123"
        val token = jwtUtil.generateToken(userId)
        assertNotNull(token, "Token 不应为 null")
        assertTrue(token.isNotBlank(), "Token 不应为空")

        val extractedUserId = jwtUtil.extractUserId(token)
        assertEquals(userId, extractedUserId, "解析的用户ID应该与原始用户ID相同")
    }
}
