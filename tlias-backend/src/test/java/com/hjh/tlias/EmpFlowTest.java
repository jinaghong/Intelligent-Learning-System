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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4a 员工条件分页查询的验收测试。
 *
 * 和 DeptFlowTest 一样：先写、先跑红，再实现。
 *
 * 种子数据里 15 个员工的分布（data.sql 定死的，断言依赖它）：
 *   性别：男 8 人 / 女 7 人
 *   入职日期：2015-03-16 ~ 2023-09-11
 *   姓名里带「丽」的：黄丽、孙丽，共 2 人
 *   2020 年入职的：刘洋、黄丽、孙丽，共 3 人
 *
 * 类上的 @Transactional 保证用例跑完自动回滚，种子数据不会被弄脏。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmpFlowTest {

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
    void loginFirst() throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        token = objectMapper.readTree(body).path("data").path("token").asText();
    }

    // ------------------------------------------------------------------
    //  分页
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 不带任何条件：返回第一页，total 是总条数而不是当页条数")
    void listShouldReturnFirstPageByDefault() throws Exception {
        JsonNode page = query();

        System.out.println("\n[1] 默认分页 = " + page);
        assertEquals(15, page.path("total").asInt(), "total 应该是全表 15 条");
        assertEquals(10, page.path("rows").size(), "默认 pageSize 是 10，第一页应该 10 条");
    }

    @Test
    @DisplayName("2. page/pageSize 生效：第二页 5 条，且和第一页不重叠")
    void pageAndPageSizeShouldShiftWindow() throws Exception {
        List<Long> firstIds = idsOf(query("page", "1", "pageSize", "5"));
        List<Long> secondIds = idsOf(query("page", "2", "pageSize", "5"));

        System.out.println("\n[2] 第一页 id = " + firstIds + "，第二页 id = " + secondIds);
        assertEquals(5, secondIds.size());
        assertTrue(Collections.disjoint(firstIds, secondIds), "两页的内容不能重叠");
    }

    // ------------------------------------------------------------------
    //  筛选条件
    // ------------------------------------------------------------------

    @Test
    @DisplayName("3. 姓名是模糊匹配：搜「丽」能搜到黄丽和孙丽")
    void nameFilterShouldBeFuzzy() throws Exception {
        JsonNode page = query("name", "丽");

        List<String> names = namesOf(page);
        System.out.println("\n[3] 搜「丽」得到 = " + names);
        assertEquals(2, page.path("total").asInt());
        assertTrue(names.contains("黄丽") && names.contains("孙丽"), "两个名字里带丽的都应该被搜到");
    }

    @Test
    @DisplayName("4. 性别是精确匹配：查男只出男")
    void genderFilterShouldBeExact() throws Exception {
        JsonNode page = query("gender", "1");

        assertEquals(8, page.path("total").asInt(), "种子数据里男员工 8 人");
        for (JsonNode row : page.path("rows")) {
            assertEquals(1, row.path("gender").asInt(), "点了「男」却混进了女员工：" + row.path("name").asText());
        }
    }

    @Test
    @DisplayName("5. 入职日期区间：2020 年全年应命中 3 人")
    void entryDateRangeShouldFilterByYear() throws Exception {
        JsonNode page = query("begin", "2020-01-01", "end", "2020-12-31");

        List<String> names = namesOf(page);
        System.out.println("\n[5] 2020 年入职 = " + names);
        assertEquals(3, page.path("total").asInt());
        assertTrue(names.containsAll(List.of("刘洋", "黄丽", "孙丽")));
        assertFalse(names.contains("李伟"), "李伟是 2015 年入职的，不该出现在 2020 年的结果里");
    }

    @Test
    @DisplayName("6. 多个条件是「并且」：女 + 名字带丽 + 日期区间 → 只剩 2 人")
    void combinedFiltersShouldNarrowResults() throws Exception {
        JsonNode page = query("gender", "2", "name", "丽",
                "begin", "2019-01-01", "end", "2021-12-31");

        System.out.println("\n[6] 组合条件结果 = " + namesOf(page));
        assertEquals(2, page.path("total").asInt(), "黄丽(2020-11-03) 和 孙丽(2020-04-06) 都在区间内");
    }

    // ------------------------------------------------------------------
    //  多表关联
    // ------------------------------------------------------------------

    @Test
    @DisplayName("7. 列表每行都带 deptName（多表关联的成果）")
    void rowsShouldCarryDeptName() throws Exception {
        JsonNode page = query("name", "李伟");

        JsonNode row = page.path("rows").get(0);
        System.out.println("\n[7] 李伟这一行 = " + row);
        assertEquals("学工部", row.path("deptName").asText(), "李伟的部门应该是学工部");
    }

    @Test
    @DisplayName("8. 没有部门的员工也要出现在列表里（左连接，不是内连接）")
    void empWithoutDeptShouldStillBeListed() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO emp (username, password, name, gender, job, entry_date, dept_id, status)
                VALUES ('tmp_no_dept', 'not-a-real-hash', ?, 1, 1, '2024-01-01', NULL, 1)
                """, "无部门临时工");

        JsonNode page = query("name", "无部门临时工");

        assertEquals(1, page.path("total").asInt(), "没部门的员工被内连接吃掉了，应该用 LEFT JOIN");
        JsonNode row = page.path("rows").get(0);
        assertTrue(row.path("deptName").isNull(), "没部门时 deptName 应该是 null，实际 = " + row.path("deptName"));
    }

    // ------------------------------------------------------------------
    //  安全
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9. 列表里绝不能出现 password 字段")
    void listShouldNotLeakPassword() throws Exception {
        JsonNode page = query();

        assertFalse(page.path("rows").get(0).has("password"), "响应里带出了密码哈希");
    }

    @Test
    @DisplayName("10. 不带 token 访问员工接口：401")
    void listShouldRequireToken() throws Exception {
        mockMvc.perform(get("/emps"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ------------------------------------------------------------------
    //  分页参数校验（2026-10-10 审查发现的修复）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("11. page=0：业务错误码，而不是悄悄按第 1 页返回")
    void invalidPageShouldFail() throws Exception {
        mockMvc.perform(get("/emps").param("page", "0").header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("页码必须从 1 开始"));
    }

    @Test
    @DisplayName("12. pageSize=-5：业务错误码，而不是 total=0 的自相矛盾响应")
    void negativePageSizeShouldFail() throws Exception {
        mockMvc.perform(get("/emps").param("pageSize", "-5").header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("每页条数至少为 1"));
    }

    @Test
    @DisplayName("13. page 传空值（?page=）：按默认第 1 页处理，不崩")
    void emptyPageParamShouldFallBackToDefault() throws Exception {
        JsonNode page = query("page", "");

        System.out.println("\n[13] ?page= 空值 = total:" + page.path("total")
                + "，rows:" + page.path("rows").size());
        assertEquals(15, page.path("total").asInt());
        assertEquals(10, page.path("rows").size(), "空页码应该按默认 pageSize 返回第 1 页");
    }

    // ------------------------------------------------------------------
    //  测试自己的小工具
    // ------------------------------------------------------------------

    /**
     * 发一次 GET /emps，返回响应里的 data 节点（也就是 {total, rows}）。
     *
     * 参数按「名, 值, 名, 值……」成对传，例如 query("gender", "1", "page", "2")。
     *
     * 为什么不直接把中文拼进 URL 字符串？因为 MockMvc 会对 URL 再编码一次，
     * 手写 URLEncoder.encode("丽") 会被编成 %25E4%25B8%25BD，服务端收到的是
     * 字面量 "%E4%B8%BD" 而不是「丽」，查询结果永远是 0 条 —— 这个坑踩过一次。
     * 用 .param() 直接设置请求参数，始终不经过 URL 编码，最省心。
     */
    private JsonNode query(String... nameValuePairs) throws Exception {
        MockHttpServletRequestBuilder request = get("/emps");
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request = request.param(nameValuePairs[i], nameValuePairs[i + 1]);
        }

        String body = mockMvc.perform(request.header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    private List<String> namesOf(JsonNode page) {
        List<String> names = new ArrayList<>();
        for (JsonNode row : page.path("rows")) {
            names.add(row.path("name").asText());
        }
        return names;
    }

    private List<Long> idsOf(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode row : page.path("rows")) {
            ids.add(row.path("id").asLong());
        }
        return ids;
    }

}
