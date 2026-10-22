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
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P8 AI 增强的验收测试：正常路径 + 参数校验（先写、先跑红，再实现）。
 *
 * 两个刻意的设计：
 *
 * 1. 测试全程走 MockAiChatServiceImpl（默认 profile，@Profile("!deepseek")）——
 *    不打真实网络：确定性（真模型每次回答不一样，没法断言）、零成本（token 要钱）、
 *    离线可跑。真调用属于 deepseek profile，测试永不开它。
 *
 * 2. 断言全部走 JdbcTemplate 直查 ai_call_log，不经过被测代码——实现和断言
 *    不同源。类上 @Transactional：AI 日志和业务读都在测试事务里，跑完一起回滚，
 *    ai_call_log 保持 0 行，不会弄脏 DbSmokeTest 的「0 行」断言（P7 踩坑 #14 的教训）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiFlowTest {

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

    /** 有成绩、有违纪记录的学员（seed 数据保证存在），拿它当被测对象 */
    private long studentId;
    private String studentName;
    private int violationCount;

    /** 调用前已有的 AI 日志数：审计表会累积真实使用记录，断言只能看「本次新增」 */
    private int aiLogBaseline;

    @BeforeEach
    void loginAndPickStudent() throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode data = objectMapper.readTree(body).path("data");
        token = data.path("token").asText();
        loginEmpId = data.path("id").asLong();

        Map<String, Object> student = jdbcTemplate.queryForMap(
                "SELECT id, name, violation_count FROM student WHERE violation_count > 0 ORDER BY id LIMIT 1");
        studentId = ((Number) student.get("id")).longValue();
        studentName = (String) student.get("name");
        violationCount = ((Number) student.get("violation_count")).intValue();
        aiLogBaseline = aiLogCount();
    }

    // ------------------------------------------------------------------
    //  批次一：学习建议正常路径（1 ~ 6）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 生成学习建议 → 200/code=1，返回 Mock 建议与来源标识")
    void adviceShouldReturnMockAdvice() throws Exception {
        mockMvc.perform(get("/ai/advice/" + studentId)
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.studentId").value(studentId))
                .andExpect(jsonPath("$.data.studentName").value(studentName))
                .andExpect(jsonPath("$.data.model").value("mock"))
                .andExpect(jsonPath("$.data.createTime").isNotEmpty())
                .andExpect(jsonPath("$.data.advice").isNotEmpty())
                .andExpect(jsonPath("$.data.advice").value(org.hamcrest.Matchers.containsString("[MOCK]")));
    }

    @Test
    @DisplayName("2. 一次建议调用 → ai_call_log 新增一行，字段逐项核对")
    void adviceCallShouldBeLogged() throws Exception {
        mockMvc.perform(get("/ai/advice/" + studentId)
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk());

        assertEquals(aiLogBaseline + 1, aiLogCount(), "一次建议调用应该恰好产生一行 AI 调用日志");
        Map<String, Object> row = latestAiLog();
        assertEquals("ADVICE", row.get("biz_type"));
        assertEquals("mock", row.get("model"));
        assertEquals(1, ((Number) row.get("status")).intValue(), "成功的调用 status 应该是 1");
        assertEquals(loginEmpId, ((Number) row.get("emp_id")).longValue(),
                "emp_id 应该是当前登录人 liwei 的 id");
        assertTrue(((Number) row.get("cost_time")).longValue() >= 0, "耗时不应该为负");
        assertNotNull(row.get("create_time"), "调用时间不该为空");
        assertNotNull(row.get("prompt"), "prompt 不该为空");
        assertNotNull(row.get("response"), "response 不该为空");
    }

    @Test
    @DisplayName("3. 落库的 prompt 含学员姓名、违纪次数、课程成绩（拼接完整性）")
    void advicePromptShouldContainStudentData() throws Exception {
        mockMvc.perform(get("/ai/advice/" + studentId)
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk());

        String prompt = (String) latestAiLog().get("prompt");
        assertTrue(prompt.contains(studentName), "prompt 里应该含学员姓名，实际：" + prompt);
        assertTrue(prompt.contains("违纪次数：" + violationCount),
                "prompt 里应该含违纪次数（格式约定「违纪次数：N」），实际：" + prompt);

        String courseName = jdbcTemplate.queryForObject(
                "SELECT c.name FROM student_score ss JOIN course c ON ss.course_id = c.id "
                        + "WHERE ss.student_id = ? ORDER BY ss.id LIMIT 1", String.class, studentId);
        assertTrue(prompt.contains(courseName),
                "prompt 里应该含该学员的成绩课程「" + courseName + "」，实际：" + prompt);
    }

    @Test
    @DisplayName("4. 落库的 response 与接口返回的 advice 完全一致（两处同源）")
    void adviceResponseShouldMatchReturned() throws Exception {
        String body = mockMvc.perform(get("/ai/advice/" + studentId)
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String returned = objectMapper.readTree(body).path("data").path("advice").asText();
        assertEquals(returned, latestAiLog().get("response"),
                "接口返回的建议和日志表里的 response 应该是同一段文本");
    }

    @Test
    @DisplayName("5. 无 token → 401，且没有产生任何 AI 调用日志")
    void noTokenShouldBe401AndLogNothing() throws Exception {
        mockMvc.perform(get("/ai/advice/" + studentId))
                .andExpect(status().isUnauthorized());

        assertEquals(aiLogBaseline, aiLogCount(), "被拦截的请求不该产生 AI 调用日志");
    }

    @Test
    @DisplayName("6. 学员不存在 → code 0 友好提示，且不调 AI、不写日志")
    void missingStudentShouldNotCallAi() throws Exception {
        mockMvc.perform(get("/ai/advice/999999")
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString("学员不存在")));

        assertEquals(aiLogBaseline, aiLogCount(), "业务参数错误发生在调 AI 之前，不该写 AI 调用日志");
    }

    // ------------------------------------------------------------------
    //  批次二：智能出题正常路径 + 参数校验（7 ~ 12）
    // ------------------------------------------------------------------

    /** 任一存在的课程（seed 数据保证有），拿它当出题对象 */
    private long courseId;
    private String courseName;

    private void pickCourse() {
        Map<String, Object> course = jdbcTemplate.queryForMap(
                "SELECT id, name FROM course ORDER BY id LIMIT 1");
        courseId = ((Number) course.get("id")).longValue();
        courseName = (String) course.get("name");
    }

    @Test
    @DisplayName("7. 智能出题 → 200/code=1，返回数量精确、四字段齐全")
    void questionsShouldReturnParsedQuestions() throws Exception {
        pickCourse();
        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token)
                        .param("courseId", String.valueOf(courseId))
                        .param("count", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].stem").isNotEmpty())
                .andExpect(jsonPath("$.data[0].options.length()").value(4))
                .andExpect(jsonPath("$.data[0].answer").isNotEmpty())
                .andExpect(jsonPath("$.data[0].analysis").isNotEmpty());
    }

    @Test
    @DisplayName("8. 一次出题调用 → ai_call_log 记 QUESTION，prompt 含课程名与数量")
    void questionCallShouldBeLogged() throws Exception {
        pickCourse();
        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token)
                        .param("courseId", String.valueOf(courseId))
                        .param("count", "3"))
                .andExpect(status().isOk());

        assertEquals(aiLogBaseline + 1, aiLogCount(), "一次出题调用应该恰好产生一行日志");
        Map<String, Object> row = latestAiLog();
        assertEquals("QUESTION", row.get("biz_type"));
        assertEquals(1, ((Number) row.get("status")).intValue());
        String prompt = (String) row.get("prompt");
        assertTrue(prompt.contains(courseName), "prompt 里应该含课程名，实际：" + prompt);
        assertTrue(prompt.contains("题目数量：3"), "prompt 里应该含数量（格式约定「题目数量：N」），实际：" + prompt);
    }

    @Test
    @DisplayName("9. count 越界（0 / 21）→ code 0 友好提示，不产生日志")
    void invalidCountShouldFail() throws Exception {
        pickCourse();
        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token)
                        .param("courseId", String.valueOf(courseId))
                        .param("count", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("出题数量至少 1 道"));

        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token)
                        .param("courseId", String.valueOf(courseId))
                        .param("count", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("出题数量最多 20 道"));

        assertEquals(aiLogBaseline, aiLogCount(), "参数校验失败发生在调 AI 之前，不该写日志");
    }

    @Test
    @DisplayName("10. courseId 缺失 / difficulty 越界 → code 0 友好提示")
    void invalidCourseAndDifficultyShouldFail() throws Exception {
        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("课程 id 不能为空"));

        pickCourse();
        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token)
                        .param("courseId", String.valueOf(courseId))
                        .param("difficulty", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("难度必须在 1~3 之间"));

        assertEquals(aiLogBaseline, aiLogCount(), "参数校验失败发生在调 AI 之前，不该写日志");
    }

    @Test
    @DisplayName("11. 课程不存在 → code 0 提示，不调 AI、不写日志")
    void missingCourseShouldNotCallAi() throws Exception {
        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token)
                        .param("courseId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("课程不存在"));

        assertEquals(aiLogBaseline, aiLogCount(), "业务参数错误发生在调 AI 之前，不该写 AI 调用日志");
    }

    @Test
    @DisplayName("12. 缺省参数：count 缺省=5、difficulty 缺省=中等（prompt 体现）")
    void defaultsShouldBeUsed() throws Exception {
        pickCourse();
        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token)
                        .param("courseId", String.valueOf(courseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.length()").value(5));

        String prompt = (String) latestAiLog().get("prompt");
        assertTrue(prompt.contains("题目数量：5"), "count 缺省应该是 5，实际：" + prompt);
        assertTrue(prompt.contains("难度：中等"), "difficulty 缺省应该是中等，实际：" + prompt);
    }

    // ------------------------------------------------------------------
    //  辅助：直查 ai_call_log
    // ------------------------------------------------------------------

    private int aiLogCount() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_call_log", Integer.class);
        return n == null ? 0 : n;
    }

    private Map<String, Object> latestAiLog() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ai_call_log ORDER BY id DESC LIMIT 1");
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

}
