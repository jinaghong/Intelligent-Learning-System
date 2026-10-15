# P7 AOP 操作日志 · 计划书

> 创建：2026-10-15
> 状态：设计已确认（用户 2026-10-15 四项拍板），待实施
> 分工：全部实现 + 用例由我写，用户驱动验收（原计划书的「我搭切面，你贴注解」经用户改选为「我全写」，沿用 P5/P6 实际模式）
> 前置：P6 已提交（`c33f694`，144/144 全绿）；`oper_log` 表已于 P0.5 建好（0 行、11 字段、刻意无外键）
> 关联：`docs/项目进度.md`

---

## 一、目标与范围

给系统加上**操作日志**：谁、什么时候、调了哪个接口方法、传了什么参数、
花了多少毫秒、成功还是失败（失败带异常信息）、从哪个 IP 发的——全部自动落进 `oper_log` 表。
业务代码一行日志逻辑都不写，靠自定义注解 `@Log` + AOP 切面统一拦截。

另加一个只读接口 `GET /logs`（分页、时间倒序、需登录），让验收和 P9 前端日志页有抓手。

四项已拍板的决策：

| 决策点 | 结论 |
|---|---|
| 分工 | 我全写 + 用例，用户驱动验收 |
| 写入方式 | **同步**写入（与黑马课程一致；测试随事务回滚不留脏数据） |
| 记录范围 | 全部业务写操作（16 个增删改）+ 登录 + 上传，共 **18 个方法** |
| 查询接口 | 加 `GET /logs` 分页查询 |

明确不做（YAGNI）：

| 不做 | 理由 |
|---|---|
| 异步写日志（@Async） | 用户已选同步；异步的 ThreadLocal 跨线程问题只作为面试知识点讲解，不落地 |
| 日志的修改 / 删除接口 | 审计日志只增不删是基本原则；清理属于运维脚本，不做成功能 |
| 按操作人 / 时间段筛选 | `GET /logs` 只做纯分页；筛选等 P9 有真实页面需求时再加 |
| 登出（logout）日志 | logout 目前无实际动作（JWT 无状态），记它没有信息量 |
| GET 查询类操作 | 查询太频繁，记了淹没有价值的写操作；黑马课程同样只记增删改 |

---

## 二、AOP 一分钟原理（教学锚点）

```
请求 → JWT 拦截器（验身份，已有）→ 【LogAspect：开始计时】
     → Controller 方法（贴了 @Log 的才会被切面拦住）
     → 【LogAspect：拿到返回值或异常 → 组装日志行 → 插入 oper_log】
     → 返回值原样交回 / 异常原样上抛
```

类比：切面是「传送带旁的质检员」——贴着 `@Log` 标签的包裹经过时自动登记，
包裹里的东西（业务逻辑）完全不用动。AOP（面向切面编程）解决的就是
「同一件和业务无关的事散落在几十个方法里」的问题：日志、权限、事务都是典型场景。

---

## 三、贴 `@Log` 的方法清单（18 个）

| Controller | 方法 |
|---|---|
| LoginController | login（登录成功失败都记，带 IP） |
| DeptController | save / update / delete |
| EmpController | save / update / delete |
| CourseController | save / update / delete |
| ClazzController | save / update / delete |
| StudentController | save / update / delete / addViolation |
| UploadController | upload |

`EmpController.export` 是 GET（只读导出），不贴。

---

## 四、实现结构（9 个新文件 + 2 处改动）

```
annotation/Log.java                  自定义注解（@Target METHOD + @Retention RUNTIME）
aspect/LogAspect.java                核心切面：@Around 计时、取操作人/IP、序列化、落库
entity/OperLog.java                  oper_log 表实体（11 字段）
mapper/OperLogMapper.java            extends BaseMapper<OperLog>
service/LogService.java              record(OperLog) + page(LogQueryDTO)
service/impl/LogServiceImpl.java     插入 + MP 分页（operate_time、id 双倒序）
controller/LogController.java        GET /logs（@Tag("操作日志")，需登录）
dto/LogQueryDTO.java                 page/pageSize（@Min(1)，沿用 P4 修复后的校验套路）
test/OperLogFlowTest.java            验收用例（见第六节）
```

改动：

1. `pom.xml` 加 `spring-boot-starter-aop`（版本由 parent 托管，无需写版本号）
2. 上述 18 个方法各贴一个 `@Log`；`db/schema.sql` 里 oper_log 的注释
   「异步写入」改为「同步写入」（见第七节偏差说明）

---

## 五、关键设计决策（每条都是面试点）

1. **密码脱敏**：`POST /emps`、登录的入参里有明文密码，直接 JSON 序列化会把密码
   存进日志表——真实漏洞。切面序列化入参时递归扫描 JSON 树，把 `password` 字段
   统一替换成 `******`。
2. **异常也记、记完原样抛出**：业务抛异常 → 切面记 `status=0` + 异常信息，
   然后**原样往上抛**，全局异常处理器照常转成友好提示。切面不能吞异常。
3. **日志写入失败不能搞挂业务**：落库动作包 try/catch，失败只打 error 日志——
   审计丢了可以补，业务挂了是事故。
4. **`@Valid` 校验失败不产生日志**：参数校验发生在「进方法之前」，
   切面拦不到——这是机制特性不是 bug，测试钉死它（校验失败 → 无新日志行）。
5. **登录日志的 `emp_id` 为 NULL**：登录时拦截器还没验出身份，操作人未知；
   审计价值体现在 `method_params` 里的用户名（密码已脱敏）+ IP + 成功/失败。
6. **不能序列化的参数直接跳过**：`MultipartFile` / `HttpServletRequest` /
   `HttpServletResponse` / `BindingResult` 不进 `method_params`
   （否则上传接口会把整个文件字节塞进日志表）。

---

## 六、验收标准（Definition of Done）

### A. 用例（13 条，TDD 先写先红再转绿，分两批）

**批次一：写入路径（9 条）**

1. 新增部门成功 → `oper_log` 新增一行：class_name 含 `DeptController`、
   method_name=`save`、emp_id=当前登录人、status=1、cost_time≥0、
   operate_time 非空、ip 非空、method_params 含部门名、return_value 含 `"code":1`
2. 重名新增部门失败 → status=0、error_msg 含「部门名称已存在」、return_value 为 null
3. 新增员工（带密码）→ method_params 里 password=`******` 且不含明文 `123456`
4. 登录成功 → method_name=`login`、status=1、emp_id 为 NULL、params 密码已脱敏
5. 登录失败（密码错）→ status=0、error_msg 与业务提示一致
6. `@Valid` 校验失败（空部门名）→ **不产生**新日志行
7. 上传文件 → 产生日志且 method_params 不含文件内容（MultipartFile 被过滤）
8. 无 token 调写接口 → 401 且**不产生**日志行（拦截器在切面之前拦下）
9. 违纪处理 → method_name=`addViolation`、method_params 含 studentId；
   且同一次请求只记一行（切面无重复织入）

**批次二：查询接口（4 条）**

10. `GET /logs` 分页：code=1、rows 按时间倒序（第一行 = 最近一次操作）、
    total 与 oper_log 直查一致
11. `page=0` → 业务错误码（BindException 路径，沿用 P4 修复）
12. `pageSize=0` → 业务错误码
13. 无 token `GET /logs` → 401

### B. 独立对账

日志行的字段断言以 JdbcTemplate 直查 `oper_log` 为准，不经过被测代码自身的任何查询接口。

### C. 真服务 curl E2E

起真服务：登录 → 新增一个部门 → `GET /logs` 第一行就是刚才的新增操作
（class/method/status/耗时齐全）→ 删掉该部门收尾不留脏数据。

### D. 全量套件两轮全绿

144 旧 + 13 新 = **157/157** 两轮 BUILD SUCCESS。

---

## 七、与 schema 注释的偏差

`db/schema.sql` 里 oper_log 的注释写的是「AOP 切面**异步**写入」，
实施改为**同步**写入（用户拍板）。理由：同步与黑马课程一致、测试随事务回滚干净；
异步的收益（不拖慢业务）在本项目数据量下可忽略。注释随本次提交修正。
异步方案作为面试延伸知识点讲解（ThreadLocal 不跨线程、需先快照操作人信息），不落地。
