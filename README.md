# tlias 智能学习辅助系统

以黑马程序员 tlias 模块划分为骨架，加入 AI 增强模块与三层可替换抽象（缓存 / 文件存储 / AI），
兼作练手项目与面试素材。

业务域：部门、员工、班级、学员、违纪扣分、成绩、课程、数据统计、AI 学习建议与智能出题。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / 运行时 | Java 21 |
| 后端框架 | Spring Boot 3.5.16 |
| 持久层 | MyBatis-Plus 3.5.17 + MySQL 8.0.36 |
| AI | Spring AI 1.1.8（`spring-ai-starter-model-openai` 指向 DeepSeek） |
| 缓存 | Caffeine（`CacheService` 抽象，可切 Redis） |
| 文件存储 | 本地磁盘（`FileStorageService` 抽象，可切阿里云 OSS） |
| 认证 | JWT + 拦截器 + ThreadLocal |
| 前端 | Vue3 + Vite + Element Plus + Pinia + ECharts |

## 目录结构

```
.
├── tlias-backend/    后端（Spring Boot 单模块）
├── tlias-frontend/   前端（P9 阶段创建）
└── docs/             模块说明、面试追问清单、踩坑记录
```

## 本地启动

```bash
cd tlias-backend
mvn spring-boot:run
```

启动后访问健康检查接口：

```
GET http://localhost:8080/api/health
```

返回统一响应体：

```json
{"code":1,"msg":null,"data":{"status":"UP","application":"tlias-backend","time":"..."}}
```

`code` 为 1 表示成功，0 表示失败。

## 配置分层与敏感信息

| 文件 | 是否入库 | 内容 |
|---|---|---|
| `application.yml` | 是 | 公共配置：应用名、端口、context-path、profile 分组 |
| `application-dev.yml` | 是 | 开发环境非敏感配置：日志级别、数据库 url/username |
| `application-local.yml` | **否** | 真实密码与 API Key，已被 `.gitignore` 排除 |
| `application-local.yml.example` | 是 | 空模板，列出需要填的键 |

首次运行请复制模板并填入真实值：

```bash
cd tlias-backend/src/main/resources
cp application-local.yml.example application-local.yml
```

`application.yml` 里配置了 profile 分组，激活 `dev` 会自动连带激活 `local`，
因此无需每次手敲 `--spring.profiles.active=dev,local`。

## 实施阶段

P0 工程骨架 → P0.5 建库建表 → P1 通用层 → P2 登录认证 → P3 部门管理 →
P4 员工管理 → P5 学员/班级/课程 → P6 数据统计 → P7 AOP 操作日志 →
P8 AI 增强 → P9 前端整合

当前进度：**P0 已完成**。
