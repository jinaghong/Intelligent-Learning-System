package com.hjh.tlias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjh.tlias.config.properties.JwtProperties;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 部门员工名单的验收测试。
 *
 * 复用员工分页和员工导出两条既有链路，只补 deptId 过滤；
 * 测试从库里现取部门/员工，避免把种子数量写死。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmpDeptRosterTest {

    private static final String USERNAME = "liwei";
    private static final String PASSWORD = "123456";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

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

    @Test
    @DisplayName("1. 员工分页支持 deptId 过滤")
    void pageShouldFilterByDept() throws Exception {
        Long deptId = firstDeptWithEmployees();
        Long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM emp WHERE dept_id = ?", Long.class, deptId);

        String body = mockMvc.perform(get("/emps")
                        .param("deptId", String.valueOf(deptId))
                        .param("page", "1")
                        .param("pageSize", "100")
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode page = objectMapper.readTree(body).path("data");

        System.out.println("\n[1] deptId=" + deptId + " 员工数 = " + page.path("total").asInt());
        assertEquals(expected.intValue(), page.path("total").asInt());
        for (JsonNode row : page.path("rows")) {
            assertEquals(deptId.longValue(), row.path("deptId").asLong(),
                    "分页结果里混进了别的部门的员工");
        }
    }

    @Test
    @DisplayName("2. 部门导出只含该部门员工，文件名带部门名")
    void exportShouldOnlyContainDeptEmployees() throws Exception {
        Long deptId = firstDeptWithEmployees();
        String deptName = jdbcTemplate.queryForObject(
                "SELECT name FROM dept WHERE id = ?", String.class, deptId);
        List<String> expectedNames = jdbcTemplate.queryForList(
                "SELECT name FROM emp WHERE dept_id = ? ORDER BY id", String.class, deptId);

        String disposition = mockMvc.perform(get("/emps/export")
                        .param("deptId", String.valueOf(deptId))
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX_CONTENT_TYPE))
                .andReturn().getResponse().getHeader("Content-Disposition");
        assertNotNull(disposition);
        String encoded = disposition.substring(disposition.indexOf("utf-8''") + 7);
        String fileName = URLDecoder.decode(encoded.replace("+", "%20"), StandardCharsets.UTF_8).replace(".xlsx", "");
        System.out.println("\n[2] 部门导出文件名 = " + fileName);
        assertEquals(deptName + "-员工名单", fileName);

        List<List<String>> cells = readXlsx(download(get("/emps/export")
                .param("deptId", String.valueOf(deptId))));
        assertEquals(List.of("用户名", "姓名", "性别", "职位", "入职日期", "部门", "状态"),
                cells.get(0), "导出表头不对");
        assertEquals(1 + expectedNames.size(), cells.size(), "导出条数应等于该部门员工数");
        List<String> actualNames = cells.subList(1, cells.size()).stream()
                .map(row -> row.get(1))
                .toList();
        assertEquals(expectedNames, actualNames, "导出内容混进了别的部门员工");
        for (List<String> row : cells.subList(1, cells.size())) {
            assertEquals(deptName, row.get(5), "部门列不正确");
        }
    }

    @Test
    @DisplayName("3. 空部门导出只有表头")
    void emptyDeptShouldExportHeaderOnly() throws Exception {
        String name = "空部门" + System.currentTimeMillis() % 1_000_000;
        jdbcTemplate.update("INSERT INTO dept (name) VALUES (?)", name);
        Long deptId = jdbcTemplate.queryForObject(
                "SELECT id FROM dept WHERE name = ?", Long.class, name);

        List<List<String>> cells = readXlsx(download(get("/emps/export")
                .param("deptId", String.valueOf(deptId))));

        System.out.println("\n[3] 空部门导出总行数 = " + cells.size());
        assertEquals(1, cells.size(), "空部门应该只有表头");
    }

    private Long firstDeptWithEmployees() {
        return jdbcTemplate.queryForObject("""
                SELECT dept_id FROM emp
                WHERE dept_id IS NOT NULL
                GROUP BY dept_id
                ORDER BY COUNT(*) DESC
                LIMIT 1
                """, Long.class);
    }

    private byte[] download(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX_CONTENT_TYPE))
                .andReturn().getResponse().getContentAsByteArray();
    }

    private List<List<String>> readXlsx(byte[] bytes) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    cells.add(formatter.formatCellValue(row.getCell(c)));
                }
                rows.add(cells);
            }
        }
        return rows;
    }

}
