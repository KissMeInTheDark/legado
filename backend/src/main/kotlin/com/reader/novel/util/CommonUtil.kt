package com.reader.novel.util

import org.apache.commons.codec.digest.DigestUtils

/**
 * 通用工具方法集合
 */
object CommonUtil {

    /**
     * 根据书籍 URL 生成书籍唯一 ID
     * 使用 MD5 哈希，保证长度固定（32位）且唯一
     *
     * @param bookUrl Legado 原始书籍 URL
     * @return 32位 MD5 哈希字符串（小写）
     */
    fun generateBookId(bookUrl: String): String = DigestUtils.md5Hex(bookUrl.trim())

    /**
     * 计算文本字数（过滤空白字符后统计）
     *
     * @param content 章节正文文本
     * @return 字数
     */
    fun countWords(content: String): Int = content.replace(Regex("\\s+"), "").length
}
