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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P3 部门管理的验收测试。
 *
 * 这些用例是**先写、先跑红**的：Dept 那六个文件还没写，现在跑起来
 * 每一条都会因为接口不存在而失败。失败信息就是待办清单。
 *
 * 两个刻意的设计：
 *
 * 1. 全程只用 JSON 字符串和 jsonPath 断言，**不引用任何 Dept 相关类**。
 *    这样在实现还不存在时测试也能编译 —— 否则「测试无法编译」和
 *    「测试失败」会混在一起，你分不清是自己写错了还是功能没做。
 *
 * 2. 类上标了 @Transactional，每个测试方法跑完自动回滚。
 *    这些用例会真的往库里增删改部门，不回滚的话种子数据会被弄脏，
 *    DbSmokeTest 里「部门表 6 行」的断言就会莫名其妙变红。
 *    MockMvc 和测试方法跑在同一个线程里，共用同一个数据库连接，
 *    所以 Service 里的写入能被这个事务接管并一起回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeptFlowTest {

    /** 种子数据里已有的员工，密码是 123456 */
    private static final String USERNAME = "liwei";
    private static final String PASSWORD = "123456";

    /** 一个肯定不存在的部门 id，用来测「不存在」的分支 */
    private static final long MISSING_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 每个用例前先登录一次，拿到 token 备用 */
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
    //  1 ~ 3：查询
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 查询全部部门：code=1，且能按中文名找到种子部门")
    void listShouldReturnSeedDepts() throws Exception {
        String body = mockMvc.perform(get("/depts").header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode rows = objectMapper.readTree(body).path("data");
        System.out.println("\n[1] 部门列表 = " + rows);

        assertTrue(rows.isArray(), "data 应该是一个数组");
        assertTrue(rows.size() >= 6, "至少应该有 6 条种子部门数据，实际 " + rows.size());
        assertTrue(findDeptIdByName("学工部") > 0, "列表里应该有学工部（顺便验证中文没有乱码）");
    }

    @Test
    @DisplayName("2. 按 id 查询：拿到的是对应的那个部门")
    void getByIdShouldReturnMatchingDept() throws Exception {
        long id = findDeptIdByName("教研部");

        String body = mockMvc.perform(get("/depts/" + id).header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.name").value("教研部"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // id 单独拎出来比：JSON 里的小整数会被解析成 Integer，而这里期望的是 long，
        // 类型对不上时 jsonPath().value() 会判不相等，属于测试自己制造的假失败
        assertEquals(id, objectMapper.readTree(body).path("data").path("id").asLong());
    }

    @Test
    @DisplayName("3. 按 id 查询一个不存在的部门：业务错误码而不是 500")
    void getMissingIdShouldFail() throws Exception {
        mockMvc.perform(get("/depts/" + MISSING_ID).header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("部门不存在"));
    }

    // ------------------------------------------------------------------
    //  4 ~ 6：新增
    // ------------------------------------------------------------------

    @Test
    @DisplayName("4. 新增部门：code=1，且能在列表里查到")
    void addShouldCreateDept() throws Exception {
        mockMvc.perform(post("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"后勤部\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        assertTrue(findDeptIdByName("后勤部") > 0, "新增之后列表里应该能查到后勤部");
    }

    @Test
    @DisplayName("5. 新增重名部门：业务错误码 + 提示「部门名称已存在」")
    void addDuplicateNameShouldFail() throws Exception {
        createDept("测试部甲");

        mockMvc.perform(post("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试部甲\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("部门名称已存在"));
    }

    @Test
    @DisplayName("6. 新增空名字（纯空格）：业务错误码 + 提示不能为空")
    void addBlankNameShouldFail() throws Exception {
        mockMvc.perform(post("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("部门名称不能为空"));
    }

    // ------------------------------------------------------------------
    //  7 ~ 10：修改
    // ------------------------------------------------------------------

    @Test
    @DisplayName("7. 修改部门名：code=1，旧名字消失、新名字出现")
    void updateShouldRenameDept() throws Exception {
        long id = createDept("测试部乙");

        mockMvc.perform(put("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"name\":\"测试部丙\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        assertTrue(findDeptIdByName("测试部丙") > 0, "改名之后应该能查到新名字");
        assertTrue(findDeptIdByName("测试部乙") < 0, "旧名字应该已经不存在了");
    }

    @Test
    @DisplayName("8. 修改时名字原样不动（等于没改）：必须成功，不能被自己挡住")
    void updateWithItsOwnNameShouldSucceed() throws Exception {
        long id = createDept("测试部丁");

        mockMvc.perform(put("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"name\":\"测试部丁\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
    }

    @Test
    @DisplayName("9. 修改成一个别的部门已经占用的名字：业务错误码 + 提示重名")
    void updateToAnotherDeptNameShouldFail() throws Exception {
        long id = createDept("测试部戊");

        mockMvc.perform(put("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + id + ",\"name\":\"学工部\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("部门名称已存在"));
    }

    @Test
    @DisplayName("10. 修改一个不存在的部门：业务错误码 + 提示部门不存在")
    void updateMissingIdShouldFail() throws Exception {
        mockMvc.perform(put("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + MISSING_ID + ",\"name\":\"随便什么名字\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("部门不存在"));
    }

    // ------------------------------------------------------------------
    //  11 ~ 12：删除（P3 的重点）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("11. 删除一个没有员工的部门：成功，且列表里真的没了")
    void deleteDeptWithoutEmployeesShouldSucceed() throws Exception {
        long id = createDept("测试部己");

        mockMvc.perform(delete("/depts/" + id).header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        assertTrue(findDeptIdByName("测试部己") < 0, "删除之后列表里不该再有它");
    }

    @Test
    @DisplayName("12. 删除一个还有员工的部门：业务错误码，且该部门必须原封不动")
    void deleteDeptWithEmployeesShouldFail() throws Exception {
        // 不写死部门 id —— 从库里现找一个「确实有员工」的部门，种子数据改了也不会失效
        Integer deptIdWithEmp = jdbcTemplate.queryForObject(
                "SELECT dept_id FROM emp WHERE dept_id IS NOT NULL LIMIT 1", Integer.class);
        assertTrue(deptIdWithEmp != null, "种子数据里应该有归属部门的员工");

        mockMvc.perform(delete("/depts/" + deptIdWithEmp).header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("该部门下还有员工，不能删除"));

        Integer stillThere = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dept WHERE id = ?", Integer.class, deptIdWithEmp);
        assertTrue(stillThere != null && stillThere == 1, "删除失败后部门必须还在，不能被删掉");
    }

    // ------------------------------------------------------------------
    //  13：权限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("13. 不带 token 访问部门接口：401")
    void deptShouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/depts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/depts/1"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  下面两个是测试自己的小工具，不是被测对象
    // ------------------------------------------------------------------

    /**
     * 按名字去列表里找部门 id，找不到返回 -1。
     * 为什么不直接查库？因为这样顺带把「新增/修改之后列表接口能不能查到」也验了。
     */
    private long findDeptIdByName(String name) throws Exception {
        String body = mockMvc.perform(get("/depts").header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        for (JsonNode row : objectMapper.readTree(body).path("data")) {
            if (name.equals(row.path("name").asText())) {
                return row.path("id").asLong();
            }
        }
        return -1;
    }

    /** 造一个测试用的部门出来，返回它的 id */
    private long createDept(String name) throws Exception {
        mockMvc.perform(post("/depts")
                        .header(jwtProperties.getTokenName(), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        long id = findDeptIdByName(name);
        assertTrue(id > 0, "造测试数据失败：新增 " + name + " 之后列表里找不到它");
        return id;
    }

}
