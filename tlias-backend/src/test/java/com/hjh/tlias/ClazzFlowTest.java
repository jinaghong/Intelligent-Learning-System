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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * P5b 班级管理的验收测试（先写、先跑红，再实现）。
 *
 * 种子数据里 6 个班级（id 1~6），班主任来自 emp：
 *   1 JavaEE 就业 1 期 → 王芳(emp 2)      2 JavaEE 就业 2 期 → 张敏(emp 3)
 *   3 前端开发 1 期 → 刘洋(emp 4)         4 大数据 1 期 → 陈静(emp 5)
 *   5 Python 1 期 → 郭青(emp 14)          6 测试 1 期 → 王芳(emp 2)
 * 开班日期：1/2/4/5 期在 2026 年，3/6 期在 2025 年。
 * 每个班都有 8 个学员（48 ÷ 6），所以「删班级」的拦截在种子数据上直接可测。
 *
 * 计划书点名的验收标准之一「班级列表带班主任姓名」就在这个测试里
 * ——它靠 LEFT JOIN emp 实现，不是某一列直接读出来的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClazzFlowTest {

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
    @DisplayName("1. 不带条件：6 个班按 id 升序返回")
    void listShouldReturnAllByDefault() throws Exception {
        JsonNode page = query();

        System.out.println("\n[1] 班级总数 = " + page.path("total").asInt());
        assertEquals(6, page.path("total").asInt());
        assertEquals("JavaEE 就业 1 期", page.path("rows").get(0).path("name").asText());
    }

    @Test
    @DisplayName("2. 列表带班主任姓名：id=1 的班 → 王芳（LEFT JOIN emp 查出来的）")
    void listShouldCarryMasterName() throws Exception {
        JsonNode page = query("page", "1", "pageSize", "1");

        System.out.println("\n[2] 第一行 = " + page.path("rows").get(0));
        assertEquals("王芳", page.path("rows").get(0).path("masterName").asText(),
                "班主任姓名应该来自 emp 表的关联查询");
    }

    @Test
    @DisplayName("3. 没有班主任的班不能被抹掉：新建一个 masterId 为空的班，列表里仍然有它")
    void clazzWithoutMasterShouldStillAppear() throws Exception {
        Map<String, Object> body = clazzBody("临时无班主任班");
        postJson("/clazzs", body).andExpect(jsonPath("$.code").value(1));

        JsonNode page = query("name", "临时无班主任班");
        JsonNode row = page.path("rows").get(0);
        System.out.println("\n[3] 无班主任的班 = " + row);
        assertEquals(1, page.path("total").asInt(), "LEFT JOIN 不该把它从列表里抹掉");
        // 这个字段要么是「存在但为 null」，要么干脆不出现，两种都算正确；
        // 但绝不能是别人家的班主任名字
        assertTrue(row.get("masterName") == null || row.get("masterName").isNull());
    }

    @Test
    @DisplayName("4. 班级名称模糊匹配：搜「JavaEE」出 2 个班")
    void nameFilterShouldBeFuzzy() throws Exception {
        JsonNode page = query("name", "JavaEE");

        System.out.println("\n[4] 搜 JavaEE = " + page.path("total").asInt() + " 个");
        assertEquals(2, page.path("total").asInt());
    }

    @Test
    @DisplayName("5. 开班日期区间：2026 年开班的 4 个班（1/2/4/5 期）")
    void beginDateRangeShouldFilter() throws Exception {
        JsonNode page = query("begin", "2026-01-01", "end", "2026-12-31");

        System.out.println("\n[5] 2026 年开班 = " + page.path("total").asInt() + " 个");
        assertEquals(4, page.path("total").asInt());
    }

    @Test
    @DisplayName("6. /clazzs/list：不分页，6 个班全给（给学员表单下拉用）")
    void listAllEndpointShouldReturnEverything() throws Exception {
        JsonNode list = readData(getWith("/clazzs/list"));

        System.out.println("\n[6] 下拉列表 = " + list.size() + " 个班");
        assertEquals(6, list.size());
        assertEquals("王芳", list.get(0).path("masterName").asText());
    }

    // ------------------------------------------------------------------
    //  详情
    // ------------------------------------------------------------------

    @Test
    @DisplayName("7. 详情：id=3 → 前端开发 1 期，班主任刘洋，学科 2，已结课")
    void detailShouldReturnFullInfo() throws Exception {
        JsonNode clazz = readData(getWith("/clazzs/3"));

        System.out.println("\n[7] id=3 = " + clazz);
        assertEquals("前端开发 1 期", clazz.path("name").asText());
        assertEquals("刘洋", clazz.path("masterName").asText());
        assertEquals(2, clazz.path("subject").asInt());
        assertEquals(2, clazz.path("status").asInt(), "前端开发 1 期是已结课");
    }

    @Test
    @DisplayName("8. 详情查一个不存在的班级：业务错误码")
    void detailMissingShouldFail() throws Exception {
        expectBusinessError(getWith("/clazzs/" + MISSING_ID), "班级不存在");
    }

    // ------------------------------------------------------------------
    //  新增
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9. 新增班级：code=1，总数变 7")
    void saveShouldPersistClazz() throws Exception {
        postJson("/clazzs", clazzBody("测试 2 期"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        JsonNode page = query();
        System.out.println("\n[9] 新增后总数 = " + page.path("total").asInt());
        assertEquals(7, page.path("total").asInt());
    }

    @Test
    @DisplayName("10. 新增重名班级：业务错误码 + 提示名称已存在")
    void saveDuplicateNameShouldFail() throws Exception {
        expectBusinessError(postJson("/clazzs", clazzBody("JavaEE 就业 1 期")), "班级名称已存在");
    }

    @Test
    @DisplayName("11. 新增时班主任 id 不存在：业务错误码，而不是系统繁忙")
    void saveBadMasterShouldFail() throws Exception {
        Map<String, Object> body = clazzBody("测试 2 期");
        body.put("masterId", MISSING_ID);

        expectBusinessError(postJson("/clazzs", body), "班主任不存在");
    }

    @Test
    @DisplayName("12. 新增时结班日期早于开班日期：业务错误码（日期先后是业务规则）")
    void saveReversedDatesShouldFail() throws Exception {
        Map<String, Object> body = clazzBody("测试 2 期");
        body.put("beginDate", "2026-12-01");
        body.put("endDate", "2026-01-01");

        expectBusinessError(postJson("/clazzs", body), "结班日期不能早于开班日期");
    }

    @Test
    @DisplayName("13. 新增时学科编码不在 1~5：参数校验拦下（学科枚举一致性）")
    void saveBadSubjectShouldFail() throws Exception {
        Map<String, Object> body = clazzBody("测试 2 期");
        body.put("subject", 9);

        expectBusinessError(postJson("/clazzs", body), "学科编码必须是 1~5");
    }

    // ------------------------------------------------------------------
    //  修改
    // ------------------------------------------------------------------

    @Test
    @DisplayName("14. 修改班级：改名 + 换班主任都真的生效")
    void updateShouldPersistChanges() throws Exception {
        Map<String, Object> body = clazzBody("Python 1 期（2026 秋季班）");
        body.put("id", 5);
        body.put("masterId", 2);
        body.put("beginDate", "2026-07-01");
        body.put("endDate", "2027-03-01");
        body.put("subject", 4);

        putJson("/clazzs", body).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        JsonNode clazz = readData(getWith("/clazzs/5"));
        System.out.println("\n[14] 修改后 = " + clazz.path("name").asText()
                + "，班主任 = " + clazz.path("masterName").asText());
        assertEquals("Python 1 期（2026 秋季班）", clazz.path("name").asText());
        assertEquals("王芳", clazz.path("masterName").asText(), "班主任应该从郭青换成王芳");
    }

    @Test
    @DisplayName("15. 修改时班级名原样提交（等于没改）：必须成功，不能被自己挡住")
    void updateKeepingOwnNameShouldSucceed() throws Exception {
        // 先把 id=5 当前的信息读出来，原样回填——避免在测试里硬编码种子数据细节
        JsonNode before = readData(getWith("/clazzs/5"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", 5);
        body.put("name", before.path("name").asText());
        body.put("room", before.path("room").asText());
        body.put("beginDate", before.path("beginDate").asText());
        body.put("endDate", before.path("endDate").asText());
        body.put("masterId", before.path("masterId").asLong());
        body.put("subject", before.path("subject").asInt());
        body.put("status", before.path("status").asInt());

        putJson("/clazzs", body).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
    }

    // ------------------------------------------------------------------
    //  删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("16. 删除还有学员的班级：业务错误码，班级必须原封不动")
    void deleteClazzWithStudentsShouldFail() throws Exception {
        Long clazzId = jdbcTemplate.queryForObject("SELECT id FROM clazz LIMIT 1", Long.class);
        assertNotNull(clazzId);

        expectBusinessError(deleteWith("/clazzs/" + clazzId), "该班级下还有学员，不能删除");

        // 再确认班级真的还在
        readData(getWith("/clazzs/" + clazzId));
    }

    @Test
    @DisplayName("17. 删除一个没有学员的新班级：成功，列表里真的没了")
    void deleteFreshClazzShouldSucceed() throws Exception {
        postJson("/clazzs", clazzBody("待删除的空班")).andExpect(jsonPath("$.code").value(1));
        Long newId = jdbcTemplate.queryForObject(
                "SELECT id FROM clazz WHERE name = '待删除的空班'", Long.class);

        deleteWith("/clazzs/" + newId)
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        JsonNode page = query();
        System.out.println("\n[17] 删除后总数 = " + page.path("total").asInt());
        assertEquals(6, page.path("total").asInt());
    }

    // ------------------------------------------------------------------
    //  测试自己的小工具
    // ------------------------------------------------------------------

    /** 发一次 GET /clazzs，参数按「名, 值」成对传；返回 data 节点（{total, rows}） */
    private JsonNode query(String... nameValuePairs) throws Exception {
        var request = get("/clazzs");
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request = request.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }

        String body = mockMvc.perform(request.header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    /** 一个「合法的最小班级」请求体，各用例在它基础上增删字段 */
    private Map<String, Object> clazzBody(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("room", "A999");
        body.put("beginDate", "2026-08-01");
        body.put("endDate", "2027-04-30");
        body.put("subject", 1);
        body.put("status", 1);
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
