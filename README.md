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
| AI | Spring AI 1.1.8（`spring-ai-starter-model-openai` 指向 DeepSeek；默认 Mock 实现，激活 `deepseek` profile 才真调用） |
| 缓存 | Caffeine（`CacheService` 抽象，可切 Redis） |
| 文件存储 | 本地磁盘（`FileStorageService` 抽象，可切阿里云 OSS） |
| 认证 | JWT + 拦截器 + ThreadLocal |
| 前端 | Vue 3.5 + Vite 6 + Element Plus 2.14 + Pinia 2.3 + Vue Router 4.6 + Axios 1.20 + ECharts 5.6（纯 JavaScript） |

## 目录结构

```
.
├── tlias-backend/    后端（Spring Boot 单模块）
├── tlias-frontend/   前端（Vue3 SPA，P9 全部完成：9 个页面，含 AI 助手）
└── docs/             设计/计划书、面试追问清单、踩坑记录、验收截图
```

## 本地启动

### 后端

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

### 前端

```bash
cd tlias-frontend
npm install
npm run dev
```

- 开发地址：`http://127.0.0.1:5173`（`/api` 由 Vite 代理到 `http://localhost:8080`）
- 演示账号：`liwei / 123456`
- 生产构建：`npm run build`，产物在 `tlias-frontend/dist`
- 当前已实现：登录、主布局、首页看板（4 KPI + 8 图）、部门管理、员工管理
  （条件分页 / 头像上传 / 工作经历 / 批量删除 / Excel 导出）、课程管理、
  班级管理（班主任 / 日期校验）、学员管理（详情 / 违纪累计 / 批量删除）、
  操作日志（分页 / JSON 详情 / 凭证脱敏）、AI 助手（学习建议 / 智能出题）
- 生产构建与预览：

  ```bash
  npm run build
  npm run preview   # http://127.0.0.1:4173，已配置 /api 代理
  ```

- Element Plus 按组件子路径导入、ECharts 按需注册；`dist` 约 1.8MB，
  最大 chunk 558KB（gzip 193KB）

浏览器验收脚本（需后端 + 前端已启动，且 Chrome 带 `--remote-debugging-port=9222`）：

```bash
cd tlias-frontend
node scripts/verify-preview.mjs http://127.0.0.1:5173 ../docs/p9-preview
node scripts/verify-batch2.mjs http://127.0.0.1:5173 ../docs/p9-preview
node scripts/verify-batch3.mjs http://127.0.0.1:5173 ../docs/p9-preview
node scripts/verify-batch4.mjs http://127.0.0.1:5173 ../docs/p9-preview
# 真 key（先把后端切到 dev,deepseek）
node scripts/verify-deepseek.mjs http://127.0.0.1:5173 ../docs/p9-preview
```

生产产物验收把 URL 换成 `http://127.0.0.1:4173` 即可。

脚本做真实登录/点击/上传/导出，断言 DOM、请求头、控制台错误并截图存证；
测试数据与上传文件会自动清理。

## 配置分层与敏感信息

| 文件 | 是否入库 | 内容 |
|---|---|---|
| `application.yml` | 是 | 公共配置：应用名、端口、context-path、profile 分组 |
| `application-dev.yml` | 是 | 开发环境非敏感配置：日志级别、数据库 url/username |
| `application-deepseek.yml` | 是 | 激活 `deepseek` profile 时启用真实 AI 调用（非敏感配置 + key 占位符） |
| `application-local.yml` | **否** | 真实密码与 API Key，已被 `.gitignore` 排除 |
| `application-local.yml.example` | 是 | 空模板，列出需要填的键 |

首次运行请复制模板并填入真实值：

```bash
cd tlias-backend/src/main/resources
cp application-local.yml.example application-local.yml
```

`application.yml` 里配置了 profile 分组，激活 `dev` 会自动连带激活 `local`，
因此无需每次手敲 `--spring.profiles.active=dev,local`。

## AI 功能（P8）

两个只读接口（都需登录，见 Swagger）：`GET /api/ai/advice/{studentId}`（学习建议）、
`GET /api/ai/questions?courseId=&count=&difficulty=`（智能出题）。

**默认不激活 `deepseek` profile 时走 Mock 实现**：接口照常能调、不花 token，
结果带 `[MOCK]` 标记。切到真实 DeepSeek：

1. 在 `application-local.yml` 写 `spring.ai.openai.api-key: sk-xxx` 或设环境变量 `DEEPSEEK_API_KEY`
2. 以 `dev,deepseek` 激活 profile 启动：`mvn spring-boot:run -Dspring-boot.run.profiles=dev,deepseek`

每次调用的 prompt / 响应 / token 用量 / 耗时 / 成功失败都写进 `ai_call_log` 表；
AI 调用失败时会降级（学习建议返回规则版摘要、出题返回友好提示），日志记 `status=0`。

前端「AI 助手」页可直接体验两个能力；页面会显示返回的 `model` 字段
（`mock` / 真实模型名 / `FALLBACK`），据此判断是否真的调用了大模型。
2026-10-21 已从浏览器页面真实验证：后端 `dev,deepseek` 下建议与出题均返回
`model=deepseek-flash`，`ai_call_log` 两条 `status=1`（截图
`docs/p9-preview/23-ai-deepseek.png` / `24-ai-deepseek-questions.png`）。

## 实施阶段

P0 工程骨架 → P0.5 建库建表 → **P1 通用层** → P2 登录认证 → P3 部门管理 →
P4 员工管理 → P5 学员/班级/课程 → P6 数据统计 → P7 AOP 操作日志 →
P8 AI 增强 → P9 前端整合

| 阶段 | 状态 |
|---|---|
| P0 工程骨架 | ✅ 完成 |
| P0.5 建库建表 | ✅ 完成（10 张表 + 253 行种子数据，已在 MySQL 8.0.36 实测验证） |
| P1a 基础设施 | ✅ 完成（MyBatis-Plus、数据源、统一分页与异常处理） |
| P1b 抽象层 | ✅ 完成（CacheService 可切 Redis；FileStorageService 可切 OSS，已真连验证） |
| P2 登录认证 | ✅ 完成 |
| P3~P7 业务 / 统计 / 日志 | ✅ 完成（部门、员工、班级、学员、课程、数据统计、AOP 操作日志） |
| P8 AI 增强 | ✅ 完成（Mock 默认 + deepseek profile 真实现；174/174 全绿；已用真 key 验证 `deepseek-flash`） |
| P9 前端整合 | ✅ 完成（登录/布局/看板/部门/员工/课程/班级/学员/日志/AI 助手；dev 与生产构建均验收；AI 页面真 key E2E 通过） |

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
