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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4b 员工增删改 + 工作经历一对多的验收测试。
 *
 * 同样先写、先跑红。种子数据里：
 *   emp id=1（李伟）有 1 条工作经历
 *   emp id=4（刘洋）一条工作经历都没有
 *
 * 所有写入都发生在 @Transactional 开的那个事务里，跑完自动回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmpWriteTest {

    private static final String ADMIN_USERNAME = "liwei";
    private static final String ADMIN_PASSWORD = "123456";
    private static final long MISSING_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void loginFirst() throws Exception {
        token = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertNotNull(token, "拿不到 token，后面的用例没法跑");
    }

    // ------------------------------------------------------------------
    //  回显
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 回显：带部门名和工作经历列表")
    void detailShouldReturnDeptNameAndExperiences() throws Exception {
        JsonNode detail = readData(getWith("/emps/1"));

        System.out.println("\n[1] 李伟详情 = " + detail);
        assertEquals("李伟", detail.path("name").asText());
        assertEquals("学工部", detail.path("deptName").asText());

        int expected = countOf("SELECT COUNT(*) FROM emp_experience WHERE emp_id = 1");
        assertEquals(expected, detail.path("exprList").size(), "工作经历条数和库里对不上");
        assertEquals(1, expected, "种子数据里李伟应该有 1 条工作经历");
        assertFalse(detail.path("exprList").get(0).path("company").asText().isBlank());
    }

    @Test
    @DisplayName("2. 回显一个不存在的员工：业务错误码")
    void detailMissingIdShouldFail() throws Exception {
        expectBusinessError(getWith("/emps/" + MISSING_ID), "员工不存在");
    }

    // ------------------------------------------------------------------
    //  新增
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3. 新增：员工和它的工作经历一起落库")
    void saveShouldPersistEmpAndExperiences() throws Exception {
        Map<String, Object> body = empBody("zhaoxin", "赵新");
        body.put("exprList", List.of(
                experience("2016-03-01", "2019-06-30", "字节跳动", "后端开发"),
                experience("2019-07-01", "2024-05-31", "阿里巴巴", "高级工程师")));

        postJson("/emps", body).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        long id = findEmpIdByUsername("zhaoxin");
        JsonNode detail = readData(getWith("/emps/" + id));

        System.out.println("\n[3] 新建的赵新 = " + detail);
        assertEquals("赵新", detail.path("name").asText());
        assertEquals(2, detail.path("exprList").size(), "两条工作经历应该都存进去了");
        assertEquals("字节跳动", detail.path("exprList").get(0).path("company").asText());
        assertEquals(2, countOf("SELECT COUNT(*) FROM emp_experience WHERE emp_id = " + id));
    }

    @Test
    @DisplayName("4. 新增重名用户名：业务错误码")
    void saveDuplicateUsernameShouldFail() throws Exception {
        expectBusinessError(postJson("/emps", empBody("liwei", "冒牌李伟")), "用户名已存在");
    }

    @Test
    @DisplayName("5. 新增不给密码：业务错误码，而不是数据库报 NOT NULL")
    void saveWithoutPasswordShouldFail() throws Exception {
        Map<String, Object> body = empBody("nopwd", "没密码");
        body.remove("password");

        expectBusinessError(postJson("/emps", body), "密码不能为空");
    }

    @Test
    @DisplayName("6. 新增存的必须是 BCrypt 密文，而且拿它真能登录")
    void savedPasswordShouldBeBcryptAndUsableForLogin() throws Exception {
        postJson("/emps", empBody("newsir", "新老师")).andExpect(jsonPath("$.code").value(1));

        String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM emp WHERE username = 'newsir'", String.class);
        System.out.println("\n[6] 库里的密码 = " + stored);
        assertNotNull(stored);
        assertTrue(stored.startsWith("$2a$10$"), "存的不是 BCrypt 密文，而是：" + stored);
        assertFalse(stored.contains("123456"), "密码被明文存进去了");

        assertNotNull(login("newsir", "123456"), "用新增时给的密码登录不上，说明加密和校验对不上");
    }

    @Test
    @DisplayName("7. 新增不传状态：默认为在职")
    void saveShouldDefaultStatusToOnDuty() throws Exception {
        Map<String, Object> body = empBody("nostatus", "没状态");
        body.remove("status");

        postJson("/emps", body).andExpect(jsonPath("$.code").value(1));

        JsonNode detail = readData(getWith("/emps/" + findEmpIdByUsername("nostatus")));
        assertEquals(1, detail.path("status").asInt(), "没传状态时应该默认 1（在职）");
    }

    // ------------------------------------------------------------------
    //  修改
    // ------------------------------------------------------------------

    @Test
    @DisplayName("8. 修改：工作经历被整体替换（旧的没了，新的在）")
    void updateShouldReplaceExperiences() throws Exception {
        long id = createEmp("expupdate", "改经历");

        Map<String, Object> body = empBody("expupdate", "改经历");
        body.put("id", id);
        body.put("exprList", List.of(experience("2020-01-01", "2020-12-31", "老东家", "实习生")));
        putJson("/emps", body).andExpect(jsonPath("$.code").value(1));
        assertEquals(1, countOf("SELECT COUNT(*) FROM emp_experience WHERE emp_id = " + id));

        body.put("exprList", List.of(
                experience("2021-01-01", "2022-12-31", "新东家", "工程师"),
                experience("2023-01-01", "2024-12-31", "更新的东家", "高级工程师")));
        putJson("/emps", body).andExpect(jsonPath("$.code").value(1));

        JsonNode detail = readData(getWith("/emps/" + id));
        System.out.println("\n[8] 改过之后 = " + detail.path("exprList"));
        assertEquals(2, detail.path("exprList").size(), "旧的经历应该被删干净，只剩新的两条");
        assertEquals("新东家", detail.path("exprList").get(0).path("company").asText());
        assertEquals(2, countOf("SELECT COUNT(*) FROM emp_experience WHERE emp_id = " + id),
                "库里应该只剩 2 条，先删后插没删干净或者插重了");
    }

    @Test
    @DisplayName("9. 修改时不传密码：老密码照样能登录")
    void updateShouldKeepPasswordWhenOmitted() throws Exception {
        long id = createEmp("keeppwd", "不改密码");

        Map<String, Object> body = empBody("keeppwd", "改了名字");
        body.put("id", id);
        body.remove("password");
        putJson("/emps", body).andExpect(jsonPath("$.code").value(1));

        assertNotNull(login("keeppwd", "123456"), "没传密码却把密码改掉了，老密码登不上了");
        assertEquals("改了名字", jdbcTemplate.queryForObject(
                "SELECT name FROM emp WHERE id = " + id, String.class));
    }

    @Test
    @DisplayName("10. 修改时传了新密码：新密码立刻生效")
    void updateWithNewPasswordShouldTakeEffect() throws Exception {
        long id = createEmp("newpwd", "换密码");

        Map<String, Object> body = empBody("newpwd", "换密码");
        body.put("id", id);
        body.put("password", "newpass888");
        putJson("/emps", body).andExpect(jsonPath("$.code").value(1));

        assertNotNull(login("newpwd", "newpass888"), "换了密码后用新密码登不上");
        assertNull(login("newpwd", "123456"), "老密码居然还能用，说明新密码没写进去");
    }

    @Test
    @DisplayName("11. 修改一个不存在的员工：业务错误码")
    void updateMissingIdShouldFail() throws Exception {
        Map<String, Object> body = empBody("ghost", "查无此人");
        body.put("id", MISSING_ID);

        expectBusinessError(putJson("/emps", body), "员工不存在");
    }

    // ------------------------------------------------------------------
    //  批量删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("12. 批量删除：员工没了，他的工作经历也一起没了")
    void deleteShouldRemoveEmpsAndTheirExperiences() throws Exception {
        Map<String, Object> bodyA = empBody("delA", "待删甲");
        bodyA.put("exprList", List.of(experience("2020-01-01", "2021-01-01", "待删公司", "工程师")));
        postJson("/emps", bodyA).andExpect(jsonPath("$.code").value(1));
        long a = findEmpIdByUsername("delA");
        long b = createEmp("delB", "待删乙");

        assertEquals(1, countOf("SELECT COUNT(*) FROM emp_experience WHERE emp_id = " + a),
                "造数据时应该给甲配了 1 条经历");

        mockMvc.perform(delete("/emps").header(jwtProperties.getTokenName(), token)
                        .param("ids", a + "," + b))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        assertEquals(0, countOf("SELECT COUNT(*) FROM emp WHERE id IN (" + a + "," + b + ")"));
        assertEquals(0, countOf("SELECT COUNT(*) FROM emp_experience WHERE emp_id IN (" + a + "," + b + ")"),
                "emp_experience 的外键是 ON DELETE CASCADE，应该跟着员工一起删掉");
    }

    @Test
    @DisplayName("13. 批量删除但一个 id 都没传：业务错误码，而不是 500")
    void deleteWithoutIdsShouldFail() throws Exception {
        mockMvc.perform(delete("/emps").header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("请选择要删除的员工"));
    }

    // ------------------------------------------------------------------
    //  权限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("14. 写接口不带 token：401")
    void writeEndpointsShouldRequireToken() throws Exception {
        mockMvc.perform(post("/emps").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/emps").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/emps").param("ids", "1"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  边界与参数校验（2026-10-10 审查发现的修复）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("15. 修改时不带 exprList：工作经历原样保留（null = 不动）")
    void updateWithoutExprListShouldKeepExperiences() throws Exception {
        Map<String, Object> body = empBody("keepExp", "保留经历");
        body.put("exprList", List.of(experience("2020-01-01", "2021-01-01", "老东家", "工程师")));
        postJson("/emps", body).andExpect(jsonPath("$.code").value(1));
        long id = findEmpIdByUsername("keepExp");

        Map<String, Object> update = empBody("keepExp", "保留经历改");
        update.put("id", id);
        // 刻意不带 exprList —— 省略字段不能被当成「清空」
        putJson("/emps", update).andExpect(jsonPath("$.code").value(1));

        assertEquals(1, countOf("SELECT COUNT(*) FROM emp_experience WHERE emp_id = " + id),
                "没传 exprList 却把工作经历清空了——null 必须是「不动」");
    }

    @Test
    @DisplayName("16. 修改时传空列表 []：工作经历被显式清空")
    void updateWithEmptyExprListShouldClearExperiences() throws Exception {
        Map<String, Object> body = empBody("clearExp", "清空经历");
        body.put("exprList", List.of(experience("2020-01-01", "2021-01-01", "老东家", "工程师")));
        postJson("/emps", body).andExpect(jsonPath("$.code").value(1));
        long id = findEmpIdByUsername("clearExp");

        Map<String, Object> update = empBody("clearExp", "清空经历");
        update.put("id", id);
        update.put("exprList", List.of());
        putJson("/emps", update).andExpect(jsonPath("$.code").value(1));

        assertEquals(0, countOf("SELECT COUNT(*) FROM emp_experience WHERE emp_id = " + id),
                "传了空列表却还留着老经历——显式清空没生效");
    }

    @Test
    @DisplayName("17. 修改时 deptId 不存在：业务错误码「部门不存在」，数据原样不动")
    void updateWithBadDeptIdShouldFail() throws Exception {
        long id = createEmp("badDeptU", "坏部门改");

        Map<String, Object> body = empBody("badDeptU", "坏部门改");
        body.put("id", id);
        body.put("deptId", 999_999);
        expectBusinessError(putJson("/emps", body), "部门不存在");

        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT dept_id FROM emp WHERE id = " + id, Long.class),
                "更新失败后 dept_id 不该被改动");
    }

    @Test
    @DisplayName("18. 修改时连 id 都没传：业务错误码「员工 id 不能为空」")
    void updateWithoutIdShouldFail() throws Exception {
        expectBusinessError(putJson("/emps", empBody("noid", "没id")), "员工 id 不能为空");
    }

    @Test
    @DisplayName("19. 新增时 deptId 不存在：业务错误码「部门不存在」")
    void saveWithBadDeptIdShouldFail() throws Exception {
        Map<String, Object> body = empBody("badDeptS", "坏部门增");
        body.put("deptId", 999_999);

        expectBusinessError(postJson("/emps", body), "部门不存在");
    }

    // ------------------------------------------------------------------
    //  测试自己的小工具
    // ------------------------------------------------------------------

    /** 一个「合法的最小员工」请求体，各用例在它基础上增删字段 */
    private Map<String, Object> empBody(String username, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", "123456");
        body.put("name", name);
        body.put("gender", 1);
        body.put("job", 2);
        body.put("entryDate", "2024-06-01");
        body.put("deptId", 1);
        return body;
    }

    private Map<String, Object> experience(String begin, String end, String company, String job) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("beginDate", begin);
        e.put("endDate", end);
        e.put("company", company);
        e.put("job", job);
        return e;
    }

    /**
     * 发一个 JSON 请求。
     *
     * characterEncoding("UTF-8") 不能省：MockMvc 默认按 ISO-8859-1 处理请求体，
     * 带中文的 JSON 会被编坏，服务端 Jackson 解析失败后返回「系统繁忙」——
     * 看起来像接口写错了，其实是测试自己把请求体搞烂了。
     */
    private ResultActions postJson(String uri, Object body) throws Exception {
        return mockMvc.perform(post(uri)
                .header(jwtProperties.getTokenName(), token)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions putJson(String uri, Object body) throws Exception {
        return mockMvc.perform(put(uri)
                .header(jwtProperties.getTokenName(), token)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(objectMapper.writeValueAsString(body)));
    }

    /** 断言这次请求返回的是「业务错误码 + 指定提示语」 */
    private void expectBusinessError(ResultActions actions, String message) throws Exception {
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(message));
    }

    /** 发一次带 token 的 GET，返回未断言的执行结果 */
    private ResultActions getWith(String uri) throws Exception {
        return mockMvc.perform(get(uri).header(jwtProperties.getTokenName(), token));
    }

    /** 对刚发出的请求断言 code=1，并返回响应里的 data 节点 */
    private JsonNode readData(ResultActions actions) throws Exception {
        String body = actions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    /** 走真实的登录接口换一个 token，失败返回 null */
    private String login(String username, String password) throws Exception {
        Map<String, Object> body = Map.of("username", username, "password", password);
        String response = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = objectMapper.readTree(response);
        if (root.path("code").asInt() != 1) {
            return null;
        }
        String t = root.path("data").path("token").asText();
        return t.isBlank() ? null : t;
    }

    private int countOf(String sql) {
        Integer n = jdbcTemplate.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    private long findEmpIdByUsername(String username) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM emp WHERE username = ?", Long.class, username);
        assertNotNull(id, "库里找不到用户名 " + username);
        return id;
    }

    /** 造一个员工出来并返回 id，省得每个用例都重写一遍 */
    private long createEmp(String username, String name) throws Exception {
        postJson("/emps", empBody(username, name)).andExpect(jsonPath("$.code").value(1));
        return findEmpIdByUsername(username);
    }

}
