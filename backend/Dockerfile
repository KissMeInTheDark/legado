# 多阶段构建 Dockerfile - Spring Boot 小说阅读服务后端
# 第一阶段：使用 Gradle 构建
FROM gradle:8.11-jdk21 AS builder

WORKDIR /app
# 先复制构建文件（利用 Docker 缓存层）
COPY build.gradle.kts settings.gradle.kts ./
# 预下载依赖（依赖未变时使用缓存）
RUN gradle dependencies --no-daemon || true

# 复制源码并构建
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# 第二阶段：运行时镜像（轻量级）
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装 curl（用于健康检查）
RUN apk add --no-cache curl

# 复制构建产物
COPY --from=builder /app/build/libs/*.jar app.jar

# 设置 JVM 参数（容器友好配置）
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseContainerSupport -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

# 暴露服务端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
