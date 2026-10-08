package com.hjh.tlias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjh.tlias.common.constant.JwtClaimsConstant;
import com.hjh.tlias.config.properties.JwtProperties;
import com.hjh.tlias.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2 登录认证的验收测试。
 *
 * 前 4 项验证的是「认证链路」，骨架搭好就应该全绿；
 * 后 2 项验证的是「登录业务逻辑」，需要你把 LoginServiceImpl.login() 写完才会通过。
 * 也就是说：现在的失败项就是你的任务清单。
 *
 * 用 MockMvc 而不是真实发 HTTP 请求，好处是不占用 8080 端口、跑得更快，
 * 而且可以在同一个 JVM 里直接从上下文里取 Bean。
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginFlowTest {

    /** 种子数据里已有的员工，密码是 123456 */
    private static final String USERNAME = "liwei";
    private static final String PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtProperties jwtProperties;

    // ------------------------------------------------------------------
    //  第一部分：认证链路（骨架已搭好，现在就应该通过）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. JWT 往返：签发后能解析回原来的 empId")
    void jwtRoundTripShouldCarryEmpId() {
        String token = jwtUtil.createToken(Map.of(JwtClaimsConstant.EMP_ID, 7L));
        System.out.println("\n[1] 签发的 token = " + token);

        Claims claims = jwtUtil.parseToken(token);
        // 注意这里用 String 转一道：jjwt 解析 JSON 时小数字会变成 Integer 而不是 Long，
        // 拦截器里也是这么处理的
        Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());

        System.out.println("[1] 解析回来的 empId = " + empId);
        assertEquals(7L, empId);
    }

    @Test
    @DisplayName("2. 不带 token 访问受保护接口 → 401")
    void requestWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/login/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("3. token 被篡改 → 401")
    void tamperedTokenShouldReturn401() throws Exception {
        String token = jwtUtil.createToken(Map.of(JwtClaimsConstant.EMP_ID, 1L));
        // 改掉签名部分的最后一个字符，签名就对不上了
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        mockMvc.perform(get("/login/me").header(jwtProperties.getTokenName(), tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. token 已过期 → 401")
    void expiredTokenShouldReturn401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        String expired = Jwts.builder()
                .claims(Map.of(JwtClaimsConstant.EMP_ID, 1L))
                .issuedAt(new Date(now - 7_200_000))
                .expiration(new Date(now - 3_600_000))   // 一小时前就过期了
                .signWith(key)
                .compact();

        mockMvc.perform(get("/login/me").header(jwtProperties.getTokenName(), expired))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  第二部分：登录业务逻辑（这一段是你 P2 要写的，写完这两个才会绿）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5. 密码错误 → 登录失败")
    void loginWithWrongPasswordShouldFail() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("6. 正确登录 → 拿到 token → 带 token 访问受保护接口 200")
    void loginThenAccessProtectedEndpointShouldReturn200() throws Exception {
        String responseBody = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = objectMapper.readTree(responseBody);
        String token = root.path("data").path("token").asText();
        System.out.println("\n[6] 登录拿到的 token = " + token);
        assertFalse(token.isBlank(), "登录成功但没有返回 token，检查第 4 步有没有调 jwtUtil.createToken");

        mockMvc.perform(get("/login/me").header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.username").value(USERNAME))
                // 密码字段绝不能出现在响应里（Emp.password 标了 WRITE_ONLY）
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

}
