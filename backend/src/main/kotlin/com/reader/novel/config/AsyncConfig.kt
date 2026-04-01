package com.reader.novel.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * 异步任务线程池配置
 * 用于章节下载等后台任务，避免阻塞主线程
 */
@Configuration
class AsyncConfig : AsyncConfigurer {

    /**
     * 书籍下载专用线程池
     * 核心线程数=2，最大=5，队列=50，避免服务器资源耗尽
     */
    @Bean(name = ["downloadTaskExecutor"])
    fun downloadTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2       // 核心线程数（常驻）
        executor.maxPoolSize = 5        // 最大线程数
        executor.queueCapacity = 50     // 等待队列深度
        executor.setThreadNamePrefix("download-")  // 线程名前缀（方便日志追踪）
        executor.setWaitForTasksToCompleteOnShutdown(true)  // 关闭时等待任务完成
        executor.setAwaitTerminationSeconds(60)
        executor.initialize()
        return executor
    }

    @Override
    override fun getAsyncExecutor(): Executor = downloadTaskExecutor()
}
