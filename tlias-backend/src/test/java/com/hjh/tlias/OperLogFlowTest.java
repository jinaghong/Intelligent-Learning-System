package com.hjh.tlias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjh.tlias.config.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P7 AOP 操作日志的验收测试（先写、先跑红，再实现）。
 *
 * 被测机制一句话：凡是贴了 @Log 的 Controller 方法，执行完（无论成功还是抛异常）
 * 都要在 oper_log 表里留下一行记录——谁调的、哪个方法、传了什么参、
 * 花了多少毫秒、成功还是失败、从哪个 IP 来的。
 *
 * 三个刻意的设计：
 *
 * 1. 定位日志行用「参数里的独特标记」（比如部门名「日志测试部A」），
 *    而不是「数总行数」——@BeforeEach 的登录本身也会产生日志，
 *    数总数会把无关行算进来。
 *
 * 2. 断言全部走 JdbcTemplate 直查 oper_log，不经过任何被测代码——
 *    实现和断言不同源，才不会「错到一块儿去」。
 *
 * 3. 类上 @Transactional：日志插入和业务写入都在同一个事务里，跑完一起回滚，
 *    oper_log 表始终保持 0 行，不会弄脏 DbSmokeTest 的「oper_log 期望 0 行」。
 */
@SpringBootTest(properties = "storage.local.dir=target/test-uploads")
@AutoConfigureMockMvc
@Transactional
class OperLogFlowTest {

    private static final String USERNAME = "liwei";
    private static final String PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;

    /** 当前登录人（liwei）的员工 id，从登录响应里取，不写死 */
    private long loginEmpId;

    @BeforeEach
    void loginFirst() throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");
        token = data.path("token").asText();
        loginEmpId = data.path("id").asLong();
    }

    // ------------------------------------------------------------------
    //  批次一：写入路径（1 ~ 9）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 新增部门成功 → 日志行 11 个字段逐项核对")
    void saveDeptShouldBeLogged() throws Exception {
        mockMvc.perform(post("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"日志测试部A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        Map<String, Object> row = findLogByParamMark("DeptController", "save", "日志测试部A");
        assertNotNull(row, "新增部门后 oper_log 里应该有一行记录");
        assertTrue(row.get("class_name").toString().contains("DeptController"),
                "class_name 应该是被调用的 Controller 类，实际 " + row.get("class_name"));
        assertEquals("save", row.get("method_name"));
        assertEquals(loginEmpId, ((Number) row.get("emp_id")).longValue(),
                "emp_id 应该是当前登录人 liwei 的 id");
        assertEquals(1, ((Number) row.get("status")).intValue(), "成功的操作 status 应该是 1");
        assertTrue(((Number) row.get("cost_time")).longValue() >= 0, "耗时不应该为负");
        assertNotNull(row.get("operate_time"), "操作时间不该为空");
        assertNotNull(row.get("ip"), "IP 不该为空");
        assertTrue(row.get("method_params").toString().contains("日志测试部A"),
                "入参 JSON 里应该能看到部门名，实际 " + row.get("method_params"));
        assertTrue(row.get("return_value").toString().contains("\"code\":1"),
                "返回值 JSON 里应该是成功的 Result，实际 " + row.get("return_value"));
        assertNull(row.get("error_msg"), "成功的操作不该有异常信息");
    }

    @Test
    @DisplayName("2. 重名新增部门失败 → status=0 + 异常信息，return_value 为 null")
    void failedSaveShouldAlsoBeLogged() throws Exception {
        mockMvc.perform(post("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"学工部\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("部门名称已存在"));

        Map<String, Object> row = findLogByParamMark("DeptController", "save", "学工部");
        assertNotNull(row, "业务失败也要记日志——审计的意义就在于失败的操作也有据可查");
        assertEquals(0, ((Number) row.get("status")).intValue(), "失败的操作 status 应该是 0");
        assertTrue(row.get("error_msg").toString().contains("部门名称已存在"),
                "error_msg 应该是业务异常的信息，实际 " + row.get("error_msg"));
        assertNull(row.get("return_value"), "抛异常的方法没有返回值，return_value 应该为 null");
    }

    @Test
    @DisplayName("3. 新增员工带密码 → 日志里的密码必须脱敏成 ******")
    void passwordShouldBeMasked() throws Exception {
        String empJson = """
                {"username":"logmaskuser","password":"123456","name":"日志测试员",
                 "gender":1,"job":2,"entryDate":"2024-01-15","deptId":1}
                """;
        mockMvc.perform(post("/emps")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(empJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        Map<String, Object> row = findLogByParamMark("EmpController", "save", "logmaskuser");
        assertNotNull(row, "新增员工应该有日志");
        String params = row.get("method_params").toString();
        assertTrue(params.contains("******"), "日志里的密码应该被替换成 ******，实际 " + params);
        assertFalse(params.contains("123456"),
                "日志里绝不能出现明文密码——这是安全红线，实际 " + params);
    }

    @Test
    @DisplayName("4. 登录成功 → 记日志，emp_id 为 NULL（登录时尚无身份），密码脱敏")
    void loginSuccessShouldBeLogged() throws Exception {
        // @BeforeEach 里的那次登录就是「成功登录」的样本，不用重复调
        Map<String, Object> row = findLogByParamMark("LoginController", "login", USERNAME);
        assertNotNull(row, "登录成功应该有日志");
        assertEquals(1, ((Number) row.get("status")).intValue());
        assertNull(row.get("emp_id"),
                "登录时拦截器还没验出身份，emp_id 应该为 NULL（审计价值在用户名 + IP + 成败）");
        String params = row.get("method_params").toString();
        assertTrue(params.contains(USERNAME), "入参里应该有用户名，实际 " + params);
        assertFalse(params.contains(PASSWORD), "入参里不能有明文密码，实际 " + params);
        assertNotNull(row.get("ip"), "登录日志必须带 IP——异地登录审计就靠它");
    }

    @Test
    @DisplayName("5. 登录失败（密码错）→ status=0 + 异常信息")
    void loginFailShouldBeLogged() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 失败的这条入参里密码是 wrong-password（脱敏后看不见值），用「最新一条 login 失败行」定位
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM oper_log WHERE class_name LIKE '%LoginController' AND method_name='login'"
                        + " AND status=0 ORDER BY id DESC LIMIT 1");
        assertTrue(row.get("error_msg").toString().contains("用户名或密码错误"),
                "error_msg 应该是业务提示，实际 " + row.get("error_msg"));
        assertNull(row.get("return_value"), "抛异常时没有返回值");
    }

    @Test
    @DisplayName("6. @Valid 校验失败（空部门名）→ 不产生日志（进方法前就被拦下）")
    void validationFailShouldNotBeLogged() throws Exception {
        long before = countLogs("DeptController", "save");

        mockMvc.perform(post("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        long after = countLogs("DeptController", "save");
        assertEquals(before, after,
                "@Valid 校验发生在进方法之前，切面拦不到，不该产生日志。这是机制特性，不是 bug");
    }

    @Test
    @DisplayName("7. 上传文件 → 记日志，但 MultipartFile 不进 method_params")
    void uploadShouldBeLoggedWithoutFileContent() throws Exception {
        mockMvc.perform(multipart("/upload")
                        .file(new MockMultipartFile("file", "log-test.png", "image/png", tinyPng()))
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM oper_log WHERE class_name LIKE '%UploadController'"
                        + " AND method_name='upload' ORDER BY id DESC LIMIT 1");
        String params = row.get("method_params").toString();
        assertFalse(params.contains("MultipartFile"),
                "文件对象不能序列化进日志，否则整个文件字节都会进日志表，实际 " + params);
        assertFalse(params.contains("log-test.png"),
                "连文件名都不该出现——MultipartFile 参数整个被跳过，实际 " + params);
    }

    @Test
    @DisplayName("8. 无 token 调写接口 → 401 且不产生日志（拦截器在切面之前）")
    void noTokenWriteShouldNotBeLogged() throws Exception {
        long before = countLogs("DeptController", "save");

        mockMvc.perform(post("/depts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"日志测试部B\"}"))
                .andExpect(status().isUnauthorized());

        assertEquals(before, countLogs("DeptController", "save"),
                "请求在拦截器就被 401 拦下，根本没走到 Controller，不该有日志");
    }

    @Test
    @DisplayName("9. 违纪处理 → 记一行且只记一行（切面无重复织入），入参含 studentId")
    void violationShouldBeLoggedExactlyOnce() throws Exception {
        Long studentId = jdbcTemplate.queryForObject("SELECT id FROM student LIMIT 1", Long.class);
        String violationJson = "{\"studentId\":" + studentId + ",\"point\":2,\"description\":\"日志测试违纪\"}";

        mockMvc.perform(post("/students/violation")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(violationJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oper_log WHERE method_name='addViolation'"
                        + " AND method_params LIKE '%" + studentId + "%'", Long.class);
        assertEquals(1L, count, "一次违纪请求应该恰好产生一行日志，实际 " + count);
    }

    // ------------------------------------------------------------------
    //  批次二：查询接口（10 ~ 13）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("10. GET /logs 分页：code=1、时间倒序（第一行=最新操作）、total 与直查一致")
    void logsPageShouldReturnLatestFirst() throws Exception {
        // 先制造一笔操作，让「最新一行」有确定的内容可断言
        mockMvc.perform(post("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"日志测试部C\"}"))
                .andExpect(status().isOk());

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oper_log", Long.class);
        Long maxId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM oper_log", Long.class);

        String body = mockMvc.perform(get("/logs")
                        .header(jwtProperties.getTokenName(), token)
                        .param("page", "1").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(total, data.path("total").asLong(), "total 应该等于 oper_log 的真实行数");
        JsonNode rows = data.path("rows");
        assertTrue(rows.isArray() && !rows.isEmpty(), "rows 不该为空");
        assertTrue(rows.size() <= 5, "pageSize=5，一页最多 5 行");
        assertEquals(maxId, rows.get(0).path("id").asLong(),
                "第一行应该是最新插入的日志（按时间倒序，id 最大的在最前）");
        assertTrue(rows.get(0).path("methodParams").asText().contains("日志测试部C"),
                "第一行就是刚才那次新增部门，实际 " + rows.get(0));
    }

    @Test
    @DisplayName("11. GET /logs page=0 → 业务错误码（沿用 P4 的 BindException 处理路径）")
    void logsPageZeroShouldBeRejected() throws Exception {
        mockMvc.perform(get("/logs")
                        .header(jwtProperties.getTokenName(), token)
                        .param("page", "0").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("12. GET /logs pageSize=0 → 业务错误码")
    void logsPageSizeZeroShouldBeRejected() throws Exception {
        mockMvc.perform(get("/logs")
                        .header(jwtProperties.getTokenName(), token)
                        .param("page", "1").param("pageSize", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("13. 无 token 查日志 → 401（审计数据当然要登录才能看）")
    void logsPageRequiresLogin() throws Exception {
        mockMvc.perform(get("/logs").param("page", "1").param("pageSize", "10"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  工具方法
    // ------------------------------------------------------------------

    /** 按「类名 + 方法名 + 入参里的独特标记」定位一行日志，取最新的一条 */
    private Map<String, Object> findLogByParamMark(String classMark, String methodName, String paramMark) {
        var rows = jdbcTemplate.queryForList(
                "SELECT * FROM oper_log WHERE class_name LIKE ? AND method_name = ?"
                        + " AND method_params LIKE ? ORDER BY id DESC LIMIT 1",
                "%" + classMark + "%", methodName, "%" + paramMark + "%");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long countLogs(String classMark, String methodName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oper_log WHERE class_name LIKE ? AND method_name = ?",
                Long.class, "%" + classMark + "%", methodName);
        return count;
    }

    /** 生成一张真实的 1×1 PNG（白名单校验看扩展名，但给个真图更贴近实战） */
    private byte[] tinyPng() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

}
