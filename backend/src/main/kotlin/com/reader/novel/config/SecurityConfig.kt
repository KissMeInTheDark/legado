package com.reader.novel.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import com.reader.novel.util.JwtFilter

/**
 * Spring Security 配置
 *
 * 策略：
 *  - 无状态 Session（JWT 认证）
 *  - 白名单：/user/login、/user/refresh-token 无需 Token
 *  - 其余所有接口需要 Bearer Token
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtFilter: JwtFilter
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // 禁用 CSRF（前后端分离，JWT 无需 CSRF 保护）
            .csrf { it.disable() }
            // 禁用 Session（使用 JWT，无需服务器端 Session）
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // 请求授权配置
            .authorizeHttpRequests { auth ->
                auth
                    // 用户登录和刷新 Token 接口不需要认证
                    .requestMatchers("/user/login", "/user/refresh-token").permitAll()
                    // 健康检查接口（供 Docker/K8s 使用）
                    .requestMatchers("/actuator/health").permitAll()
                    // 其余所有接口都需要认证
                    .anyRequest().authenticated()
            }
            // 在 UsernamePasswordAuthenticationFilter 前插入 JWT 过滤器
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
