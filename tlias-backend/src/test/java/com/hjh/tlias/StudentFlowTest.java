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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P5c 学员管理 + 违纪处理的验收测试（先写、先跑红，再实现）。
 *
 * 种子数据：48 个学员（每班 8 人），20 人有违纪记录。
 * 学员 1 王浩然（1 班，JavaEE 就业 1 期）无违纪记录——违纪用例拿他开刀。
 *
 * 计划书点名的核心验收标准就在最后一个板块：
 * 「违纪扣分后 violation_count / violation_score 正确累加（同事务）」。
 * 它比看起来多一层：两个汇总字段不能用「读出来 + 在 Java 里加」的写法更新，
 * 否则并发下两笔违纪会互相覆盖——必须让数据库执行「字段 = 字段 + 1」。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentFlowTest {

    private static final String USERNAME = "liwei";
    private static final String PASSWORD = "123456";
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
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        token = objectMapper.readTree(body).path("data").path("token").asText();
        assertNotNull(token, "拿不到 token，后面的用例没法跑");
    }

    // ------------------------------------------------------------------
    //  分页与筛选
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 不带条件：48 个学员，默认每页 10 条")
    void listShouldReturnFirstPageByDefault() throws Exception {
        JsonNode page = query();

        System.out.println("\n[1] 学员总数 = " + page.path("total").asInt());
        assertEquals(48, page.path("total").asInt());
        assertEquals(10, page.path("rows").size());
    }

    @Test
    @DisplayName("2. 姓名模糊匹配：搜「浩然」的条数和库里的 LIKE 计数一致")
    void nameFilterShouldBeFuzzy() throws Exception {
        Long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student WHERE name LIKE ?", Long.class, "%浩然%");
        JsonNode page = query("name", "浩然");

        System.out.println("\n[2] 搜「浩然」= " + page.path("total").asInt()
                + " 条（库里 " + expected + " 条）");
        assertEquals(expected.intValue(), page.path("total").asInt());
        // 第一天数据的王浩然必须在结果里
        boolean found = false;
        for (JsonNode row : page.path("rows")) {
            if (row.path("name").asText().equals("王浩然")) {
                found = true;
            }
        }
        assertTrue(found, "结果里应该有王浩然");
    }

    @Test
    @DisplayName("3. 学历精确匹配：传 4（本科）的条数和库里一致")
    void degreeFilterShouldBeExact() throws Exception {
        Long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student WHERE degree = 4", Long.class);
        JsonNode page = query("degree", "4");

        System.out.println("\n[3] 本科 = " + page.path("total").asInt() + " 人（库 " + expected + "）");
        assertEquals(expected.intValue(), page.path("total").asInt());
    }

    @Test
    @DisplayName("4. 按班级筛：1 班 8 个人，每行都带班级名")
    void clazzFilterShouldWork() throws Exception {
        JsonNode page = query("clazzId", "1");

        System.out.println("\n[4] 1 班 = " + page.path("total").asInt() + " 人");
        assertEquals(8, page.path("total").asInt());
        assertEquals("JavaEE 就业 1 期", page.path("rows").get(0).path("clazzName").asText(),
                "班级名应该来自 clazz 表的 LEFT JOIN");
    }

    @Test
    @DisplayName("5. 没有班级的学员不能被抹掉：新建一个 clazzId 为空的学员，列表里仍然有他")
    void studentWithoutClazzShouldStillAppear() throws Exception {
        Map<String, Object> body = studentBody("临时没分班", "202699099");
        body.remove("clazzId");
        postJson("/students", body).andExpect(jsonPath("$.code").value(1));

        JsonNode page = query("name", "临时没分班");
        JsonNode row = page.path("rows").get(0);
        System.out.println("\n[5] 没分班的学员 = " + row);
        assertEquals(1, page.path("total").asInt(), "LEFT JOIN 不该把他从列表里抹掉");
        assertTrue(row.get("clazzName") == null || row.get("clazzName").isNull());
    }

    // ------------------------------------------------------------------
    //  详情
    // ------------------------------------------------------------------

    @Test
    @DisplayName("6. 详情：王浩然的基本信息 + 班级名 +（空的）违纪列表")
    void detailShouldReturnInfoAndViolationList() throws Exception {
        JsonNode detail = readData(getWith("/students/1"));

        System.out.println("\n[6] 王浩然 = " + detail.path("name").asText()
                + "，班级 = " + detail.path("clazzName").asText()
                + "，违纪条数 = " + detail.path("violationList").size());
        assertEquals("王浩然", detail.path("name").asText());
        assertEquals("JavaEE 就业 1 期", detail.path("clazzName").asText());
        assertEquals(0, detail.path("violationList").size(), "种子数据里他没有违纪记录");
    }

    @Test
    @DisplayName("7. 详情查一个不存在的学员：业务错误码")
    void detailMissingShouldFail() throws Exception {
        expectBusinessError(getWith("/students/" + MISSING_ID), "学员不存在");
    }

    // ------------------------------------------------------------------
    //  新增
    // ------------------------------------------------------------------

    @Test
    @DisplayName("8. 新增学员：code=1，总数变 49，字段真的落了库")
    void saveShouldPersistStudent() throws Exception {
        postJson("/students", studentBody("测试学员甲", "202699001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        JsonNode page = query();
        System.out.println("\n[8] 新增后总数 = " + page.path("total").asInt());
        assertEquals(49, page.path("total").asInt());

        Long clazzId = jdbcTemplate.queryForObject(
                "SELECT clazz_id FROM student WHERE `no` = '202699001'", Long.class);
        assertEquals(2L, clazzId, "clazzId 应该按请求体存进去");
    }

    @Test
    @DisplayName("9. 新增重复学号：业务错误码 + 提示学号已存在")
    void saveDuplicateNoShouldFail() throws Exception {
        expectBusinessError(postJson("/students", studentBody("冒名者", "202601001")),
                "学号已存在");
    }

    @Test
    @DisplayName("10. 新增时班级 id 不存在：业务错误码，而不是系统繁忙")
    void saveBadClazzShouldFail() throws Exception {
        Map<String, Object> body = studentBody("测试学员甲", "202699001");
        body.put("clazzId", MISSING_ID);

        expectBusinessError(postJson("/students", body), "班级不存在");
    }

    @Test
    @DisplayName("11. 新增缺姓名：参数校验拦下")
    void saveBlankNameShouldFail() throws Exception {
        Map<String, Object> body = studentBody("   ", "202699001");

        expectBusinessError(postJson("/students", body), "姓名不能为空");
    }

    @Test
    @DisplayName("12. 新增学历编码不在 1~6：参数校验拦下（学历枚举一致性）")
    void saveBadDegreeShouldFail() throws Exception {
        Map<String, Object> body = studentBody("测试学员甲", "202699001");
        body.put("degree", 9);

        expectBusinessError(postJson("/students", body), "学历编码必须是 1~6");
    }

    // ------------------------------------------------------------------
    //  修改
    // ------------------------------------------------------------------

    @Test
    @DisplayName("13. 修改学员：换班级 + 改电话都真的生效")
    void updateShouldPersistChanges() throws Exception {
        Map<String, Object> body = studentBody("王浩然", "202601001");
        body.put("id", 1);
        body.put("clazzId", 2);
        body.put("phone", "13900001111");

        putJson("/students", body).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        JsonNode detail = readData(getWith("/students/1"));
        System.out.println("\n[13] 修改后班级 = " + detail.path("clazzName").asText());
        assertEquals("JavaEE 就业 2 期", detail.path("clazzName").asText());
        assertEquals("13900001111", detail.path("phone").asText());
    }

    @Test
    @DisplayName("14. 修改一个不存在的学员：业务错误码")
    void updateMissingShouldFail() throws Exception {
        Map<String, Object> body = studentBody("查无此人", "202699001");
        body.put("id", MISSING_ID);

        expectBusinessError(putJson("/students", body), "学员不存在");
    }

    // ------------------------------------------------------------------
    //  批量删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("15. 批量删除：两个新学员一起删，违纪记录被外键 CASCADE 带走")
    void batchDeleteShouldRemoveStudentsAndViolations() throws Exception {
        postJson("/students", studentBody("测试学员甲", "202699001")).andExpect(jsonPath("$.code").value(1));
        postJson("/students", studentBody("测试学员乙", "202699002")).andExpect(jsonPath("$.code").value(1));
        Long id1 = jdbcTemplate.queryForObject("SELECT id FROM student WHERE `no` = '202699001'", Long.class);
        Long id2 = jdbcTemplate.queryForObject("SELECT id FROM student WHERE `no` = '202699002'", Long.class);

        // 先给甲记一笔违纪，再删——验证 CASCADE 真的把明细带走了
        postJson("/students/violation", violationBody(id1, 5, "上课玩手机"))
                .andExpect(jsonPath("$.code").value(1));

        deleteWith("/students?ids=" + id1 + "," + id2)
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student WHERE id IN (" + id1 + "," + id2 + ")", Long.class),
                "两个学员应该都没了");
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_violation WHERE student_id = " + id1, Long.class),
                "甲的违纪记录应该被 CASCADE 带走");
    }

    @Test
    @DisplayName("16. 批量删除不带 ids：给出人话，而不是系统繁忙")
    void batchDeleteWithoutIdsShouldFail() throws Exception {
        expectBusinessError(deleteWith("/students"), "请选择要删除的学员");
    }

    // ------------------------------------------------------------------
    //  违纪处理（计划书点名的核心验收标准）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("17. 第一次违纪：明细多一条，count=1、score=5")
    void firstViolationShouldAccumulate() throws Exception {
        postJson("/students/violation", violationBody(1L, 5, "上课迟到"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        JsonNode detail = readData(getWith("/students/1"));
        System.out.println("\n[17] 违纪后 = count " + detail.path("violationCount").asInt()
                + "，score " + detail.path("violationScore").asInt());
        assertEquals(1, detail.path("violationCount").asInt());
        assertEquals(5, detail.path("violationScore").asInt());
        assertEquals(1, detail.path("violationList").size());
        assertEquals("上课迟到", detail.path("violationList").get(0).path("description").asText());
    }

    @Test
    @DisplayName("18. 第二次违纪：在第一次的基础上继续累加（count=2、score=8），不是覆盖")
    void secondViolationShouldKeepAccumulating() throws Exception {
        postJson("/students/violation", violationBody(1L, 5, "上课迟到"))
                .andExpect(jsonPath("$.code").value(1));
        postJson("/students/violation", violationBody(1L, 3, "未交作业"))
                .andExpect(jsonPath("$.code").value(1));

        JsonNode detail = readData(getWith("/students/1"));
        System.out.println("\n[18] 两次违纪后 = count " + detail.path("violationCount").asInt()
                + "，score " + detail.path("violationScore").asInt()
                + "，明细 " + detail.path("violationList").size() + " 条");
        assertEquals(2, detail.path("violationCount").asInt(), "次数应该是 1+1=2");
        assertEquals(8, detail.path("violationScore").asInt(), "扣分应该是 5+3=8");
        assertEquals(2, detail.path("violationList").size());
    }

    @Test
    @DisplayName("19. 给不存在的学员记违纪：业务错误码，且库里的明细表不新增记录")
    void violationForMissingStudentShouldFail() throws Exception {
        long before = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_violation", Long.class);

        expectBusinessError(postJson("/students/violation", violationBody(MISSING_ID, 5, "无中生有")),
                "学员不存在");

        assertEquals(before, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_violation", Long.class),
                "请求被拒后不能留下任何违纪记录");
    }

    @Test
    @DisplayName("20. 扣分传 0：参数校验拦下（扣分必须是正整数）")
    void violationWithZeroPointShouldFail() throws Exception {
        expectBusinessError(postJson("/students/violation", violationBody(1L, 0, "零分违纪")),
                "扣分必须是正整数");
    }

    @Test
    @DisplayName("21. 违纪事由为空：参数校验拦下")
    void violationWithBlankDescriptionShouldFail() throws Exception {
        expectBusinessError(postJson("/students/violation", violationBody(1L, 5, "   ")),
                "违纪事由不能为空");
    }

    // ------------------------------------------------------------------
    //  测试自己的小工具
    // ------------------------------------------------------------------

    private JsonNode query(String... nameValuePairs) throws Exception {
        var request = get("/students");
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request = request.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }

        String body = mockMvc.perform(request.header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    /** 一个「合法的最小新学员」请求体，各用例在它基础上增删字段 */
    private Map<String, Object> studentBody(String name, String no) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("no", no);
        body.put("gender", 1);
        body.put("phone", "13800000000");
        body.put("degree", 4);
        body.put("clazzId", 2);
        return body;
    }

    private Map<String, Object> violationBody(Long studentId, int point, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", studentId);
        body.put("point", point);
        body.put("description", description);
        return body;
    }

    private ResultActions getWith(String uri) throws Exception {
        return mockMvc.perform(get(uri).header(jwtProperties.getTokenName(), token));
    }

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

    private ResultActions deleteWith(String uri) throws Exception {
        return mockMvc.perform(delete(uri).header(jwtProperties.getTokenName(), token));
    }

    private void expectBusinessError(ResultActions actions, String message) throws Exception {
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(message));
    }

    private JsonNode readData(ResultActions actions) throws Exception {
        String body = actions
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

}
