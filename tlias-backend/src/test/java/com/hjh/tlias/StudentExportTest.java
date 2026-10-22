package com.hjh.tlias;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 班级学员名单导出的验收测试。
 *
 * 和 EmpExportTest 同一个标准：把响应体当成真 xlsx 解析回来逐格核对，
 * 而不是只看「接口返回 200」。种子数据里 1 班有 8 个学员。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudentExportTest {

    private static final String USERNAME = "liwei";
    private static final String PASSWORD = "123456";
    private static final long CLAZZ_ID = 1L;
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
    @DisplayName("1. 按班级导出：真 xlsx、中文表头、姓名与库一致")
    void exportShouldReturnReadableRosterForClass() throws Exception {
        byte[] bytes = download(get("/students/export").param("clazzId", String.valueOf(CLAZZ_ID)));

        assertEquals("PK", new String(bytes, 0, 2, StandardCharsets.US_ASCII),
                "不是 xlsx（xlsx 是 zip 包，应以 PK 开头）");

        List<List<String>> cells = readXlsx(bytes);
        System.out.println("\n[1] 表头 = " + cells.get(0));
        System.out.println("[1] 第一行数据 = " + cells.get(1));
        System.out.println("[1] 总行数（含表头）= " + cells.size());

        assertEquals(List.of("姓名", "学号", "性别", "手机号", "学历", "班级", "院校生源", "违纪次数", "累计扣分"),
                cells.get(0), "表头不对，或者列的顺序变了");

        List<String> expectedNames = jdbcTemplate.queryForList(
                "SELECT name FROM student WHERE clazz_id = ? ORDER BY id", String.class, CLAZZ_ID);
        assertEquals(1 + expectedNames.size(), cells.size(), "1 班应该是表头 + 库里全部学员");
        List<String> actualNames = cells.subList(1, cells.size()).stream()
                .map(row -> row.get(0))
                .toList();
        assertEquals(expectedNames, actualNames, "导出的姓名和库里 1 班的学员对不上");

        for (List<String> row : cells.subList(1, cells.size())) {
            assertEquals("JavaEE 就业 1 期", row.get(5), "班级列不正确");
            assertTrue(List.of("男", "女").contains(row.get(2)), "性别应翻译成中文：" + row);
            assertTrue(List.of("初中", "高中", "专科", "本科", "硕士", "博士").contains(row.get(4)),
                    "学历应翻译成中文：" + row);
            assertTrue(List.of("是", "否").contains(row.get(6)), "院校生源应翻译成中文：" + row);
        }
    }

    @Test
    @DisplayName("2. 下载文件名带班级名")
    void exportShouldSetClassFileName() throws Exception {
        String disposition = mockMvc.perform(get("/students/export")
                        .param("clazzId", String.valueOf(CLAZZ_ID))
                        .header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX_CONTENT_TYPE))
                .andReturn().getResponse().getHeader("Content-Disposition");

        assertNotNull(disposition);
        String encoded = disposition.substring(disposition.indexOf("utf-8''") + 7);
        String fileName = URLDecoder.decode(encoded.replace("+", "%20"), StandardCharsets.UTF_8);
        System.out.println("\n[2] 下载文件名 = " + fileName);
        assertEquals("JavaEE 就业 1 期-学员名单.xlsx", fileName);
    }

    @Test
    @DisplayName("3. 空班级导出只有表头，不报错")
    void emptyClassShouldExportHeaderOnly() throws Exception {
        String className = "空班级-导出测试";
        jdbcTemplate.update("""
                INSERT INTO clazz (name, begin_date, end_date, subject, status)
                VALUES (?, '2026-01-01', '2026-12-31', 1, 1)
                """, className);
        Long clazzId = jdbcTemplate.queryForObject(
                "SELECT id FROM clazz WHERE name = ?", Long.class, className);

        List<List<String>> cells = readXlsx(download(get("/students/export")
                .param("clazzId", String.valueOf(clazzId))));

        System.out.println("\n[3] 空班级导出总行数 = " + cells.size());
        assertEquals(1, cells.size(), "空班级应该只有表头");
        assertEquals("姓名", cells.get(0).get(0));
    }

    @Test
    @DisplayName("4. 不导出身份证、家庭住址等隐私列")
    void exportShouldNotLeakPrivateColumns() throws Exception {
        List<List<String>> cells = readXlsx(download(get("/students/export")
                .param("clazzId", String.valueOf(CLAZZ_ID))));

        List<String> header = cells.get(0);
        assertFalse(header.contains("身份证号"), "导出表头泄漏了身份证列");
        assertFalse(header.contains("家庭住址"), "导出表头泄漏了住址列");

        for (List<String> row : cells) {
            for (String cell : row) {
                assertFalse(cell.matches(".*\\d{17}[\\dXx].*"), "导出内容里出现了身份证号：" + cell);
            }
        }
    }

    @Test
    @DisplayName("5. 导出接口不带 token：401")
    void exportShouldRequireToken() throws Exception {
        mockMvc.perform(get("/students/export"))
                .andExpect(status().isUnauthorized());
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
