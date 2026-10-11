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

import java.math.BigDecimal;
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
 * P5a 课程管理的验收测试（先写、先跑红，再实现）。
 *
 * 种子数据里 8 门课程，id 1~8 顺序排列：
 *   1 Java 企业级开发 19800.00（编号 C001）…… 8 AI 应用开发 8980.00（编号 C008）
 *
 * 和部门管理（P3）是同一种「简单 CRUD」题型，多出来的考点只有一个：
 * 课程被 student_score 的外键引着（RESTRICT），删除前要先数成绩——
 * 数出来有记录就拒绝，靠外键拦的话用户看到的会是「系统繁忙」。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourseFlowTest {

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
    //  查询
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 列表：8 门课按 id 升序，金额用 BigDecimal 不丢精度")
    void listShouldReturnAllCoursesInIdOrder() throws Exception {
        JsonNode list = readData(getWith("/courses"));

        System.out.println("\n[1] 课程总数 = " + list.size() + "，首条 = " + list.get(0));
        assertEquals(8, list.size(), "种子数据一共 8 门课");
        assertEquals("Java 企业级开发", list.get(0).path("name").asText(), "第一条应该按 id 排在最前");
        // 金额用 compareTo 而不是 equals：equals 会把 19800.0 和 19800.00 当成不等，
        // 而这里关心的是「数值对不对」，不是小数位写法
        assertEquals(0, list.get(0).path("price").decimalValue().compareTo(new BigDecimal("19800.00")),
                "价格 19800.00 不能变成 19800.00000001 这种浮点误差");
    }

    @Test
    @DisplayName("2. 按 id 查询：拿到的是对应的那门课")
    void getByIdShouldReturnCourse() throws Exception {
        JsonNode course = readData(getWith("/courses/6"));

        System.out.println("\n[2] id=6 的课程 = " + course);
        assertEquals("MySQL 实战", course.path("name").asText());
        assertEquals("C006", course.path("no").asText());
    }

    @Test
    @DisplayName("3. 按 id 查询不存在的课程：业务错误码而不是 500")
    void getMissingIdShouldFail() throws Exception {
        expectBusinessError(getWith("/courses/" + MISSING_ID), "课程不存在");
    }

    // ------------------------------------------------------------------
    //  新增
    // ------------------------------------------------------------------

    @Test
    @DisplayName("4. 新增课程：code=1，总数变 9")
    void saveShouldPersistCourse() throws Exception {
        postJson("/courses", courseBody("C009", "Redis 实战", "6800.00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        JsonNode list = readData(getWith("/courses"));
        System.out.println("\n[4] 新增后总数 = " + list.size());
        assertEquals(9, list.size());
    }

    @Test
    @DisplayName("5. 新增重复编号：业务错误码 + 提示编号已存在")
    void saveDuplicateNoShouldFail() throws Exception {
        expectBusinessError(postJson("/courses", courseBody("C001", "重复编号的课", "100.00")),
                "课程编号已存在");
    }

    @Test
    @DisplayName("6. 新增缺名称：参数校验拦下，提示课程名称不能为空")
    void saveBlankNameShouldFail() throws Exception {
        Map<String, Object> body = courseBody("C009", "占位", "100.00");
        body.put("name", "   ");

        expectBusinessError(postJson("/courses", body), "课程名称不能为空");
    }

    @Test
    @DisplayName("7. 新增负价格：参数校验拦下（金额不允许为负）")
    void saveNegativePriceShouldFail() throws Exception {
        expectBusinessError(postJson("/courses", courseBody("C009", "负价格课", "-1.00")),
                "课程价格不能为负数");
    }

    // ------------------------------------------------------------------
    //  修改
    // ------------------------------------------------------------------

    @Test
    @DisplayName("8. 修改课程：名字和价格都真的变了")
    void updateShouldPersistChanges() throws Exception {
        Map<String, Object> body = courseBody("C001", "Java 企业级开发（2026 修订版）", "20800.00");
        body.put("id", 1);

        putJson("/courses", body).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        JsonNode course = readData(getWith("/courses/1"));
        System.out.println("\n[8] 修改后 = " + course);
        assertEquals("Java 企业级开发（2026 修订版）", course.path("name").asText());
        assertEquals(0, course.path("price").decimalValue().compareTo(new BigDecimal("20800.00")));
    }

    @Test
    @DisplayName("9. 修改时编号原样提交（等于没改）：必须成功，不能被自己挡住")
    void updateKeepingOwnNoShouldSucceed() throws Exception {
        Map<String, Object> body = courseBody("C001", "Java 企业级开发", "19800.00");
        body.put("id", 1);

        putJson("/courses", body).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("10. 修改一个不存在的课程：业务错误码 + 提示课程不存在")
    void updateMissingIdShouldFail() throws Exception {
        Map<String, Object> body = courseBody("C009", "给不存在的课改名", "100.00");
        body.put("id", MISSING_ID);

        expectBusinessError(putJson("/courses", body), "课程不存在");
    }

    // ------------------------------------------------------------------
    //  删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("11. 删除已有成绩记录的课程：业务错误码，课程必须原封不动")
    void deleteCourseWithScoresShouldFail() throws Exception {
        // 不写死哪门课有成绩——从库里现查一门有成绩的课，
        // 这样种子数据怎么调都不会把这条用例弄脆
        Long usedCourseId = jdbcTemplate.queryForObject(
                "SELECT course_id FROM student_score LIMIT 1", Long.class);
        assertNotNull(usedCourseId, "种子数据里应该有成绩记录，否则这条用例的前提不成立");

        expectBusinessError(deleteWith("/courses/" + usedCourseId),
                "该课程下已有成绩记录，不能删除");

        // 光看错误提示不够，再确认课程真的还在
        readData(getWith("/courses/" + usedCourseId));
    }

    @Test
    @DisplayName("12. 删除一门全新课程（没有成绩）：成功，列表里真的没了")
    void deleteFreshCourseShouldSucceed() throws Exception {
        postJson("/courses", courseBody("C009", "临时课程", "99.00"))
                .andExpect(jsonPath("$.code").value(1));
        Long newId = jdbcTemplate.queryForObject(
                "SELECT id FROM course WHERE `no` = 'C009'", Long.class);

        deleteWith("/courses/" + newId)
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        JsonNode list = readData(getWith("/courses"));
        System.out.println("\n[12] 删除后总数 = " + list.size());
        assertEquals(8, list.size(), "删完应该回到 8 门");
    }

    // ------------------------------------------------------------------
    //  测试自己的小工具
    // ------------------------------------------------------------------

    /** 一个「合法的最小课程」请求体，各用例在它基础上增删字段 */
    private Map<String, Object> courseBody(String no, String name, String price) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("no", no);
        body.put("name", name);
        body.put("price", price);
        body.put("description", "测试用课程简介");
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

    /** 断言这次请求返回的是「业务错误码 + 指定提示语」 */
    private void expectBusinessError(ResultActions actions, String message) throws Exception {
        actions.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(message));
    }

    /** 对刚发出的请求断言 code=1，并返回响应里的 data 节点 */
    private JsonNode readData(ResultActions actions) throws Exception {
        String body = actions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

}
