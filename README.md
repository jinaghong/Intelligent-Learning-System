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

P0 工程骨架 → P0.5 建库建表 → **P1 通用层** → P2 登录认证 → P3 部门管理 →
P4 员工管理 → P5 学员/班级/课程 → P6 数据统计 → P7 AOP 操作日志 →
P8 AI 增强 → P9 前端整合

| 阶段 | 状态 |
|---|---|
| P0 工程骨架 | ✅ 完成 |
| P0.5 建库建表 | ✅ 完成（10 张表 + 253 行种子数据，已在 MySQL 8.0.36 实测验证） |
| P1a 基础设施 | ✅ 完成（MyBatis-Plus、数据源、统一分页与异常处理） |
| P1b 抽象层 | ⬜ 未开始（CacheService / FileStorageService） |
| P2 及以后 | ⬜ 未开始 |

## 数据库

新建 `tlias` 库（utf8mb4 / InnoDB），与已有的 `library_db` 并存、互不影响。

| 脚本 | 作用 |
|---|---|
| `db/schema.sql` | 建库 + 建 10 张表。⚠️ 重复执行会清空重建 |
| `db/data.sql` | 253 行种子数据。可重复执行，开头会先按子表→父表顺序清空 |
| `db/verify.sql` | 建完之后的自检，14 段检查。最后两段会**故意报错**以验证约束真的生效 |

首次执行（在自己的终端里，密码自己输入）：

```
mysql -uroot -p --default-character-set=utf8mb4
```

```
source D:/qoder/Inteligent-Learning-System/tlias-backend/src/main/resources/db/schema.sql
source D:/qoder/Inteligent-Learning-System/tlias-backend/src/main/resources/db/data.sql
source D:/qoder/Inteligent-Learning-System/tlias-backend/src/main/resources/db/verify.sql
```

`--default-character-set=utf8mb4` 不能省，否则中文会存成乱码。

所有员工的登录密码统一为 `123456`，但库中 15 条 BCrypt 密文各不相同——这正是加盐的意义。

## 测试

```bash
cd tlias-backend
mvn test
```

`DbSmokeTest` 会验证数据源连通性，并把各表行数、中文编码、违纪汇总字段一致性、
密码存储形式一次性核对完毕。运行前需先在 `application-local.yml` 填好数据库密码。

## 接口文档

启动后打开 Swagger UI，可以直接在浏览器里点着调接口：

```
http://localhost:8080/api/swagger-ui/index.html
```
