# 小说阅读功能 - 开发说明

本分支实现了基于 Legado 服务端的小说阅读完整功能，包含：

- **Spring Boot 4 Kotlin 后端**（`backend/`）
- **UniApp X 前端**（`frontend/`）
- **完整开发文档**（`docs/`）
- **CI/CD 工作流**（`.github/workflows/novel-backend-ci.yml`）

## 快速开始

### 1. 部署后端服务

```bash
cd backend

# 复制并配置环境变量
cp .env.example .env
# 编辑 .env 填写 OSS、JWT 等配置

# 启动所有服务（Legado + MySQL + 后端）
docker-compose up -d

# 查看日志
docker-compose logs -f backend
```

### 2. 配置前端

编辑 `frontend/api/index.uts` 中的 `BASE_URL` 为你的后端地址：

```javascript
const BASE_URL = 'https://your-backend-domain.com/api/v1'
```

### 3. 详细文档

参见 [`docs/小说阅读功能开发文档.md`](docs/小说阅读功能开发文档.md)

## 目录结构

```
backend/                          # Spring Boot 后端
├── src/main/kotlin/com/reader/novel/
│   ├── NovelReaderApplication.kt  # 主入口
│   ├── config/                    # 配置类
│   ├── controller/                # REST 控制器
│   ├── service/                   # 业务逻辑
│   ├── entity/                    # JPA 实体
│   ├── repository/                # 数据访问
│   ├── dto/                       # 请求/响应 DTO
│   ├── exception/                 # 异常处理
│   └── util/                      # 工具类（JWT、OSS、Legado客户端）
├── src/main/resources/
│   ├── application.yml            # 配置文件
│   └── db/migration/              # Flyway SQL 脚本
├── Dockerfile                     # Docker 构建文件
├── docker-compose.yml             # 完整部署编排
└── .env.example                   # 环境变量示例

frontend/                         # UniApp X 前端
├── pages/
│   ├── index/index.uvue           # 书架页
│   ├── search/search.uvue         # 搜索页
│   ├── chapters/chapters.uvue     # 章节列表页
│   └── reader/reader.uvue         # 阅读器页
├── api/index.uts                  # API 请求层
├── utils/cache.uts                # 本地缓存工具
├── manifest.json                  # App 配置
└── pages.json                     # 页面路由配置

docs/
└── 小说阅读功能开发文档.md          # 完整中文开发文档
```

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | UniApp X (.uvue/.uts) |
| 后端 | Spring Boot 4 + Kotlin + JPA |
| 数据库 | MySQL 8.0 + Flyway 迁移 |
| 存储 | 阿里云 OSS |
| 认证 | JWT |
| 书源引擎 | Legado 服务端（Docker） |
| 部署 | Docker Compose |
| CI/CD | GitHub Actions |
