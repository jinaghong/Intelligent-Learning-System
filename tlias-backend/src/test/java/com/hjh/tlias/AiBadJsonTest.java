package com.hjh.tlias;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P8 降级路径的第二把故障开关：ai.mock.bad-json=true。
 *
 * 模拟的不是「调用失败」，而是更阴险的一类失败：**调用成功、但输出没法解析**——
 * 真模型时不时就把 JSON 包进 Markdown 围栏、或者夹带一句寒暄。
 * 这类失败必须走同一条降级轨道，且 error_msg 能看出是「解析」问题。
 *
 * 另一个测试顺带钉一个隔离性：bad-json 只影响出题（它要解析 JSON），
 * 学习建议是纯文本、不受影响——开关不能串台。
 */
@SpringBootTest(properties = "ai.mock.bad-json=true")
@AutoConfigureMockMvc
@Transactional
class AiBadJsonTest {

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

    @BeforeEach
    void login() throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        token = objectMapper.readTree(body).path("data").path("token").asText();
    }

    @Test
    @DisplayName("16. 模型输出不是 JSON → 出题降级 code 0，日志 error_msg 看得出是解析问题")
    void questionsBadJsonShouldDegrade() throws Exception {
        long courseId = ((Number) jdbcTemplate.queryForMap(
                "SELECT id FROM course ORDER BY id LIMIT 1").get("id")).longValue();

        mockMvc.perform(get("/ai/questions")
                        .header(jwtProperties.getTokenName(), token)
                        .param("courseId", String.valueOf(courseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("智能出题服务暂时不可用，请稍后重试"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM ai_call_log ORDER BY id DESC LIMIT 1");
        assertEquals(1, rows.size(), "解析失败也要留一行账");
        Map<String, Object> row = rows.get(0);
        assertEquals(0, ((Number) row.get("status")).intValue());
        assertEquals("FALLBACK", row.get("model"));
        assertTrue(row.get("error_msg").toString().contains("解析"),
                "error_msg 应该能看出是解析失败，实际：" + row.get("error_msg"));
        assertEquals("这不是合法 JSON 的内容 {{{", row.get("response"),
                "response 应原样保留模型输出，方便排查它到底回了什么");
    }

    @Test
    @DisplayName("17. 隔离性：bad-json 只影响出题，学习建议照常成功（status=1）")
    void badJsonShouldNotAffectAdvice() throws Exception {
        long studentId = ((Number) jdbcTemplate.queryForMap(
                "SELECT id FROM student ORDER BY id LIMIT 1").get("id")).longValue();

        mockMvc.perform(get("/ai/advice/" + studentId)
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.model").value("mock"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM ai_call_log ORDER BY id DESC LIMIT 1");
        assertEquals(1, ((Number) row.get("status")).intValue());
    }

}
