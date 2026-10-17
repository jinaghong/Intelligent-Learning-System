# P8 AI 增强 · 计划书

> 创建：2026-10-16
> 状态：设计已确认（用户 2026-10-16 两项拍板：**我全写 + 先 Mock 开发**；真 key 到位后再做模型名验证与真调用 E2E）
> 分工：全部实现 + 用例由我写，用户驱动验收（沿用 P5/P6/P7 模式）
> 前置：P7 已提交（`5f207b1`，157/157 全绿）；`ai_call_log` 表已于 P0.5 建好（0 行、12 字段、刻意无外键）；Spring AI 1.1.8 已在阿里云镜像**实测存在**（`mvn dependency:get` 下载成功）
> 关联：`docs/项目进度.md`

---

## 一、目标与范围

给系统加两个**只读 AI 接口**，全部走同一条链路：
**查库取数据 → 拼 Prompt → 调大模型 → 解析/落库 → 失败降级**。

架构上补齐「三层可替换抽象」的第三条腿——前两条是 cache（Caffeine/Redis）和
storage（Local/OSS），P8 加上 ai（Mock/DeepSeek），套路完全一致。

两项已拍板的决策：

| 决策点 | 结论 |
|---|---|
| 分工 | 我全写 + 用例，用户驱动验收 |
| API key | **先 Mock 开发**（不阻塞）；真实现 `@Profile("deepseek")` 备好；key 到位后 curl 验模型名 + E2E |
| 调用方式 | Spring AI 1.1.8（`spring-ai-starter-model-openai`，已实测存在） |

明确不做（YAGNI）：

| 不做 | 理由 |
|---|---|
| 异步 / 流式（SSE） | 同步请求-响应够用；流式作为面试知识点口述 |
| 题库持久化 | 生成的题只为当期使用，不落库；题库是面试口述扩展 |
| `GET /ai/logs` 查询接口 | 验收直接查库（JdbcTemplate）；P9 有页面需求再加 |
| 多轮对话 / 会话记忆 | 每次请求独立（无状态），会话记忆是另一个话题 |
| token 计费换算成人民币 | 记 token 数即可，汇率/单价是运营配置，不该硬编码 |
| 重试（retry）机制 | 降级路径已覆盖失败；重试放大会放大超时，面试口述 |

---

## 二、原理教学锚点（一分钟版）

**调大模型 = 发 HTTP 请求 + 解析 JSON。** 没有魔法。

- DeepSeek 提供的是「OpenAI 兼容协议」接口：请求打 `/chat/completions`，
  发一段 JSON（里面装着提示词），回一段 JSON（里面装着回答 + token 用量）。
  兼容协议意味着：**用 OpenAI 的调用库，改一下 base-url 就能调 DeepSeek**。
- Spring AI 是 Spring 官方的大模型客户端封装——相当于「HTTP 客户端 + 请求/响应
  对象模型 + 自动配置」，让 Java 调模型像调本地方法。
- Mock 类比：电影里的假电话道具——演员照常拨号、照常说话，电话不通到真人。
  开发/测试期用 Mock（确定性、不花钱、不依赖网络），真打电话靠 `deepseek` profile。

---

## 三、接口清单（2 个 GET，默认全拦——需登录）

| 方法 | 路径 | 返回 | 说明 |
|---|---|---|---|
| GET | `/ai/advice/{studentId}` | `AdviceVO` | 按学员的成绩 + 违纪数据生成学习建议 |
| GET | `/ai/questions` | `List<QuestionVO>` | 按课程生成题目（`courseId` 必填，`count` 默认 5，`difficulty` 默认 2） |

---

## 四、实现结构（13 个新主源码文件 + 2 个测试 + 1 个配置文件 + 4 处改动）

```
ai/                                   ← 第三条可替换抽象（对齐 cache/ storage/）
├── AiChatService.java                接口：chat(prompt) → AiChatResult
├── AiChatResult.java                 载体：content / model / promptTokens / completionTokens（record）
├── MockAiChatServiceImpl.java        @Profile("!deepseek")：默认实现，确定性返回，支持测试开关
├── DeepSeekAiChatServiceImpl.java    @Profile("deepseek")：真实现，走 Spring AI ChatClient
└── PromptTemplates.java              两个 Prompt 模板 + 围栏剥离工具（用户调优只动这一个文件）
entity/AiCallLog.java                 ai_call_log 表实体（12 字段）
mapper/AiCallLogMapper.java           extends BaseMapper<AiCallLog>（只插，不查不改——审计）
service/AiService.java                接口：advice(studentId) / questions(query)
service/impl/AiServiceImpl.java       核心编排：查数据 → 拼 Prompt → 调用 → 解析 → 写日志 → 降级
controller/AiController.java          GET /ai/advice/{studentId} + GET /ai/questions
dto/QuestionQueryDTO.java             courseId(@NotNull) / count(@Min1@Max20) / difficulty(@Min1@Max3)
vo/AdviceVO.java                      studentId / studentName / advice / model / createTime
vo/QuestionVO.java                    stem / options / answer / analysis
```

测试（沿用先红后绿）：

```
test/AiFlowTest.java        正常路径 + 参数校验（批次一、二，~12 条）
test/AiFallbackTest.java    降级路径（批次三，~5 条，@SpringBootTest 带 mock 故障开关）
```

配置文件：

```
新增 resources/application-deepseek.yml    提交版：base-url / model / 自动配置开关 / key 占位
修改 resources/application.yml             默认禁用 Spring AI 自动配置（无 key 也能启动）
修改 resources/application-local.yml.example  真实 key 的两条写法说明
修改 pom.xml                               + spring-ai-starter-model-openai:1.1.8
```

---

## 五、关键设计决策（每条都是面试点）

1. **Mock 是默认实现（`@Profile("!deepseek")`），不是临时凑数**。测试永远不打真实
   网络：确定性（模型每次回答不一样，没法断言）、零成本（token 要钱）、可离线。
   真调用靠 `dev,deepseek` 激活——和 cache 的 `!redis`/`redis`、storage 的
   `!oss`/`oss` 完全同款。
2. **降级不对称，因为「有没有可替代品」不一样**：
   - 学习建议有规则版兜底：用已有的成绩/违纪数据拼一段「规则版摘要」返回，
     仍然有信息量 → HTTP 200 + `model="FALLBACK"` 让前端能标注；
   - 智能出题没有可替代品（机器编不出题）→ 抛 `BusinessException`（现有全局
     异常处理器转成 code 0 友好提示）。
   - **两种情况都写 `ai_call_log`（status=0 + error_msg）——审计不丢。**
3. **降级日志不能被事务回滚吃掉**：`AiServiceImpl` 方法**不加 `@Transactional`**——
   出题降级是「写 status=0 日志 → 再抛异常」，如果整个方法在事务里，抛异常会连
   日志行一起回滚。审计记录必须独立提交。
4. **「学员/课程不存在」不写 `ai_call_log`**：那不是 AI 调用失败，是业务参数错误，
   发生在调 AI 之前。测试钉死「无新行」。
5. **AI 调用不贴 `@Log`**：`oper_log` 记「人做了什么操作」（P7 的账本），
   `ai_call_log` 记「AI 调用细节」（prompt / response / token / 耗时）。两本账，
   贴了就是重复记账。
6. **解析容错要防「Markdown 围栏」**：模型常把 JSON 包在 ```json ... ``` 里，
   直接 `readTree` 必炸。解析前先剥围栏；剥完解析失败 → 按降级处理（面试点：
   更稳的解法是 structured output / function calling，本期不做）。
7. **密钥分层（沿用 JWT/OSS 规矩）**：真 key 只进 `application-local.yml`
   （已 gitignore）或环境变量 `DEEPSEEK_API_KEY`；`application-deepseek.yml`
   是提交版，只放非敏感配置 + 占位符 `${DEEPSEEK_API_KEY:}`。
8. **日志写入失败不能搞挂业务**（P7 同款教训）：`ai_call_log` 的 insert 包
   try/catch，失败只打 error 日志。
9. **测试事务策略**：两个测试类都挂 `@Transactional`（P7 踩坑 #14 的直接教训——
   没事务的写会泄漏进真实库，把 DbSmokeTest 的「0 行」断言搞红）。真实 E2E
   curl 产生的行，收尾时手动清理。
10. **Mock 的故障开关**：`ai.mock.force-fail=true`（模拟网络失败）和
    `ai.mock.bad-json=true`（模拟模型输出不是 JSON），只有测试通过
    `@SpringBootTest(properties=...)` 打开——降级路径不靠运气触发，靠开关复现。

---

## 六、验收标准（Definition of Done）

三批 TDD 先红后绿，~17 条新用例（157 → ~174）。

### 批次一：学习建议正常路径（~6 条）

1. `GET /ai/advice/{id}` 带 token → 200、code=1、advice 非空且含 Mock 标记、model="mock"
2. 同请求后 `ai_call_log` 新增一行：biz_type=ADVICE、status=1、emp_id=当前登录人、
   cost_time≥0、create_time 非空
3. 落库的 prompt 里能查到学员姓名 + 各课程分数 + 违纪次数（拼接完整性）
4. 落库的 response = 接口返回的 advice（两处同源）
5. 无 token → 401，且 `ai_call_log` 无新行
6. 学员不存在 → code 0「学员不存在」类提示，且无新行

### 批次二：智能出题正常路径 + 参数校验（~6 条）

7. `GET /ai/questions?courseId=..&count=3` → 恰好 3 个 QuestionVO，
   stem/options/answer/analysis 四字段齐全
8. `ai_call_log` 新增行：biz_type=QUESTION、status=1、prompt 含课程名与数量
9. `count=0` / `count=21` → code 0 友好提示（@Min/@Max，走现有 BindException 处理器；
   项目约定：校验失败是 HTTP 200 + code 0，不是 400）
10. `courseId` 缺失 → code 0；`difficulty=9` → code 0
11. 课程不存在 → code 0 提示，且无新行
12. 缺省参数：count 缺省=5（prompt 体现）、difficulty 缺省=2（prompt 体现）

### 批次三：降级路径（~5 条，AiFallbackTest）

13. `force-fail`：建议 → 200、code=1、model=FALLBACK、advice 含数据摘要（成绩/违纪数字）且注明「规则版」
14. `force-fail`：对应 `ai_call_log` 行 status=0、model=FALLBACK、error_msg 非空
15. `force-fail`：出题 → code 0 友好提示（不是 500）
16. `bad-json`：出题 → 解析失败 → code 0；日志 status=0、error_msg 含「解析」字样
17. 隔离性：AiFlowTest（无故障开关）里同款请求 status=1——证明开关没串台

### 全量回归与 E2E

- `mvn -o test` **两轮全绿（~174/174）**，含旧 157 条不回归
- 真服务 E2E（Mock profile 即可跑通全链路）：启动 → curl 建议 + 出题 +
   mysql 直查 `ai_call_log` 对账 → 收尾 DELETE 清回 0 行 → 再跑 DbSmokeTest 确认
- （待 key）真 key 到位后：curl 验证 DeepSeek 模型名 → 更新
  `application-deepseek.yml` → `dev,deepseek` 真调用 E2E → 文档补证据
  - ✅ **2026-10-17 完成**：`/v1/models` 实测仅 `deepseek-flash` / `deepseek-v4-pro` →
    定稿 `deepseek-flash` → 真调用 E2E：建议 status=1（161+820 tokens，5.5s）、
    出题 status=1（118+873 tokens，5.5s，3 题 JSON 全部解析成功）；
    首次因 base-url 带 `/v1` 报 404，降级路径真实触发且记账正确（status=0）

---

## 七、风险与待验证（不装懂，全部列出来）

| 风险 | 处理 |
|---|---|
| DeepSeek 模型名存疑（三说并存） | ✅ **2026-10-17 真 key 实测**：平台只有 `deepseek-flash`（V4.1-Flash）/ `deepseek-v4-pro`（V4-Pro），`deepseek-chat` 已退役——原方案文档是对的；已定稿 `deepseek-flash` |
| Spring AI 的 base-url 不能带 `/v1`（会被拼成 `/v1/v1` → 404） | ✅ 已实测修正：base-url 只写 `https://api.deepseek.com`（踩坑 #16） |
| Spring AI 1.1.8 × Boot 3.5.16 兼容性 | ✅ 实测通过：编译、启动、真实调用全链路正常 |
| 无 key 时启动失败 | 默认 profile 禁用 Spring AI 自动配置（不发请求就不校验 key）；`deepseek` profile 才启用 |
| Mock 返回的 token 数 | 固定 0/0（没有真实计费）；测试对 Mock 断言 0，DeepSeek 返回真实 usage |
| 出题 JSON 稳定性 | prompt 约束 + 围栏剥离 + 降级兜底三保险；跨模型漂移是已知局限（面试点） |
| `AiCallLog` insert 与降级日志的可见性 | 决策 3 已定：业务方法不加 @Transactional，审计独立提交 |

---

## 八、面试点预告（收尾后写进 `项目进度.md`，编号 70 起）

- 为什么「接口 + profile 切换」比 if-else 判配置好（第三层抽象的复用）
- 降级设计：有替代品（规则版建议）与没替代品（出题）的两种降级姿势
- AI 调用的可观测性：prompt/response/token/耗时全落库，怎么用于成本审计与排障
- 模型输出 JSON 的容错链（围栏剥离 → 解析 → 降级），structured output 是什么
- `@Log`（操作审计）与 `ai_call_log`（AI 审计）为什么是两本账
- 密钥分层：环境变量 > local 配置 > 提交配置，为什么 key 永远不进 Git

---

## 九、施工顺序

```
批次一（建议正常路径）  写测试 → 跑红(确认红得对) → 实现 → 跑绿
批次二（出题 + 校验）   同上
批次三（降级路径）      同上
全量回归               mvn -o test ×2
真服务 E2E             curl + mysql 对账 + 清理
文档收尾               README / application-local.yml.example / 项目进度.md（P8 明细 + 踩坑 + 面试题）
提交                   计划书 + 代码一批，文档一批（沿用 P6/P7 惯例；推送等用户发话）
```
