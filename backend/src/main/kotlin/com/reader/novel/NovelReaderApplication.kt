package com.reader.novel

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

/**
 * 小说阅读服务 - 主应用入口
 *
 * 功能：
 *  1. 代理 UniApp X 前端的所有请求到 Legado 服务端
 *  2. 独立管理用户书架和阅读进度（不依赖 Legado 用户体系）
 *  3. 将书籍章节预处理后上传到阿里云 OSS，供前端按需拉取
 */
@SpringBootApplication
@EnableAsync  // 启用异步任务支持（章节下载异步执行）
class NovelReaderApplication

fun main(args: Array<String>) {
    runApplication<NovelReaderApplication>(*args)
}
