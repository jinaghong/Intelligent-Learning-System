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
 * P8 降级路径的验收测试（先写、先跑红，再实现）。
 *
 * 用 @SpringBootTest(properties = "ai.mock.force-fail=true") 打开 Mock 的
 * 故障开关——模拟「真实调用失败」的场景。为什么用开关而不是等真故障：
 * 降级路径必须可复现、可回归，靠运气触发的东西没法测试。
 *
 * 「建议」和「出题」的降级姿势刻意不同（见 计划书-P8 第五节）：
 *   - 建议有规则版兜底（数据摘要，仍有信息量）→ 200 + model=FALLBACK
 *   - 出题没有可替代品 → code 0 友好提示（不是 500）
 * 两种情况 ai_call_log 都要留一行 status=0 的账。
 */
@SpringBootTest(properties = "ai.mock.force-fail=true")
@AutoConfigureMockMvc
@Transactional
class AiFallbackTest {

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

    private long studentId;
    private String studentName;

    /** 调用前已有的 AI 日志数：审计表会累积真实使用记录，断言只能看「本次新增」 */
    private int aiLogBaseline;

    @BeforeEach
    void loginAndPickStudent() throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        token = objectMapper.readTree(body).path("data").path("token").asText();

        Map<String, Object> student = jdbcTemplate.queryForMap(
                "SELECT id, name FROM student WHERE violation_count > 0 ORDER BY id LIMIT 1");
        studentId = ((Number) student.get("id")).longValue();
        studentName = (String) student.get("name");
        aiLogBaseline = aiLogCount();
    }

    // ------------------------------------------------------------------
    //  批次三：降级路径（13 ~ 15）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("13. AI 失败 → 建议返回规则版兜底，model=FALLBACK，仍是有信息量的内容")
    void adviceForceFailShouldReturnRuleBasedFallback() throws Exception {
        mockMvc.perform(get("/ai/advice/" + studentId)
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.model").value("FALLBACK"))
                .andExpect(jsonPath("$.data.advice").value(org.hamcrest.Matchers.containsString("规则版")))
                .andExpect(jsonPath("$.data.advice").value(org.hamcrest.Matchers.containsString(studentName)))
                .andExpect(jsonPath("$.data.advice").value(org.hamcrest.Matchers.containsString("建议")));
    }

    @Test
    @DisplayName("14. AI 失败 → ai_call_log 记 status=0、model=FALLBACK、error_msg 有原因")
    void adviceForceFailShouldLogFailure() throws Exception {
        mockMvc.perform(get("/ai/advice/" + studentId)
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk());

        assertEquals(aiLogBaseline + 1, aiLogCount(), "失败的调用也要留一行账");
        Map<String, Object> row = latestAiLog();
        assertEquals("ADVICE", row.get("biz_type"));
        assertEquals(0, ((Number) row.get("status")).intValue(), "降级行的 status 应该是 0");
        assertEquals("FALLBACK", row.get("model"));
        assertNotNull(row.get("error_msg"), "失败行必须有原因");
        assertTrue(row.get("error_msg").toString().contains("模拟"),
                "error_msg 应该是原始异常信息，实际：" + row.get("error_msg"));
        assertNotNull(row.get("prompt"), "prompt 照记，方便排查当时发了什么");
        assertNotNull(row.get("response"), "response 记兜底文本，审计看得出返回了什么");
    }

    @Test
    @DisplayName("15. AI 失败 → 出题返回 code 0 友好提示（不是 500），日志同样 status=0")
    void questionsForceFailShouldReturnFriendlyError() throws Exception {
        long courseId = ((Number) jdbcTemplate.queryForMap(
                "SELECT id FROM course ORDER BY id LIMIT 1").get("id")).longValue();

        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token)
                        .param("courseId", String.valueOf(courseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("智能出题服务暂时不可用，请稍后重试"));

        assertEquals(aiLogBaseline + 1, aiLogCount());
        Map<String, Object> row = latestAiLog();
        assertEquals("QUESTION", row.get("biz_type"));
        assertEquals(0, ((Number) row.get("status")).intValue());
        assertEquals("FALLBACK", row.get("model"));
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
