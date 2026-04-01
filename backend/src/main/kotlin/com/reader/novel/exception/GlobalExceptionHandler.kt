package com.reader.novel.exception

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import com.reader.novel.dto.ApiResponse

/**
 * 业务异常：由 Service 层主动抛出，表示业务逻辑错误
 *
 * @param code 业务错误码（如 1001、1002 等，与接口文档对应）
 * @param message 错误描述（中文，返回给前端展示）
 */
class BusinessException(val code: Int, override val message: String) : RuntimeException(message)

/**
 * 全局异常处理器
 * 统一将异常转换为标准 JSON 格式返回给前端
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /** 处理业务异常（主动抛出的可预期异常） */
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("[异常处理] 业务异常 code={}, message={}", e.code, e.message)
        return ResponseEntity.ok(ApiResponse.error(e.code, e.message))
    }

    /** 处理参数校验异常（@Valid 校验失败） */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errorMsg = e.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn("[异常处理] 参数校验失败: {}", errorMsg)
        return ResponseEntity.ok(ApiResponse.error(400, "参数错误: $errorMsg"))
    }

    /** 处理未预期的异常（系统内部错误） */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("[异常处理] 系统内部错误", e)
        return ResponseEntity.ok(ApiResponse.error(500, "服务器内部错误，请稍后重试"))
    }
}
