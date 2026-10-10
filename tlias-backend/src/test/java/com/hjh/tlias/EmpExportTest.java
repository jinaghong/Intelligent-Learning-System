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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P4c Excel 导出的验收测试。
 *
 * 这个测试不满足于「接口返回了 200」——那证明不了导出的文件能打开。
 * 它会**把响应体当成一个真的 xlsx 解析回来**，逐格核对内容，
 * 这样才算真的验了「导出的文件能用 Excel 打开，内容与筛选一致」。
 *
 * 解析用的是 POI（EasyExcel 底层也是它），所以这个测试顺带保证了
 * POI 依赖是完整可用的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmpExportTest {

    private static final String USERNAME = "liwei";
    private static final String PASSWORD = "123456";

    /** xlsx 本质是个 zip 包，开头两字节固定是 PK */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtProperties jwtProperties;

    private String token;

    @BeforeEach
    void loginFirst() throws Exception {
        String body = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        token = objectMapper.readTree(body).path("data").path("token").asText();
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 导出的是一份真能打开的 xlsx：表头是中文，数据 15 行")
    void exportShouldReturnReadableXlsx() throws Exception {
        byte[] bytes = download(get("/emps/export"));

        assertTrue(bytes.length > 0, "响应体是空的，没导出任何内容");
        assertEquals("PK", new String(bytes, 0, 2, StandardCharsets.US_ASCII),
                "不是 xlsx（xlsx 是 zip 包，应以 PK 开头），前两字节 = "
                        + new String(bytes, 0, 2, StandardCharsets.US_ASCII));

        List<List<String>> cells = readXlsx(bytes);
        System.out.println("\n[1] 表头 = " + cells.get(0));
        System.out.println("[1] 第一行数据 = " + cells.get(1));
        System.out.println("[1] 总行数（含表头）= " + cells.size());

        assertEquals(List.of("用户名", "姓名", "性别", "职位", "入职日期", "部门", "状态"), cells.get(0),
                "表头不对，或者列的顺序变了");
        assertEquals(1 + 15, cells.size(), "应该是表头 1 行 + 种子数据 15 行");
    }

    @Test
    @DisplayName("2. 导出的内容跟着筛选条件走（不是永远导全表）")
    void exportShouldRespectFilters() throws Exception {
        List<List<String>> cells = readXlsx(download(get("/emps/export").param("name", "丽")));

        System.out.println("\n[2] 搜「丽」导出的内容 = " + cells);
        assertEquals(3, cells.size(), "应该只有表头 + 2 条数据");

        List<String> names = new ArrayList<>();
        for (int i = 1; i < cells.size(); i++) {
            names.add(cells.get(i).get(1));
        }
        assertEquals(List.of("黄丽", "孙丽"), names, "导出的内容和筛选条件对不上");
    }

    @Test
    @DisplayName("3. 导出的是给人看的中文，不是库里的编码")
    void exportShouldTranslateCodesToChinese() throws Exception {
        List<List<String>> cells = readXlsx(download(get("/emps/export").param("name", "李伟")));

        List<String> row = cells.get(1);
        System.out.println("\n[3] 李伟这一行 = " + row);
        // 李伟：gender=1 job=3 dept=1 status=1，直接导出去会变成一列数字
        assertEquals("男", row.get(2), "性别导出了编码而不是中文");
        assertEquals("学工主管", row.get(3), "职位导出了编码而不是中文");
        assertEquals("学工部", row.get(5));
        assertEquals("在职", row.get(6), "状态导出了编码而不是中文");
    }

    @Test
    @DisplayName("4. 导出文件里绝不能出现密码哈希")
    void exportShouldNotLeakPassword() throws Exception {
        byte[] bytes = download(get("/emps/export"));

        // 直接扫原始字节：万一某天有人往导出 VO 里加了密码字段，这里立刻红
        String raw = new String(bytes, StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("$2a$"), "导出文件里出现了 BCrypt 密码哈希");

        for (List<String> row : readXlsx(bytes)) {
            for (String cell : row) {
                assertFalse(cell.contains("$2a$"), "导出内容里有密码哈希：" + cell);
            }
        }
    }

    @Test
    @DisplayName("5. 响应头带对了：内容类型 + 附件文件名")
    void exportShouldSetDownloadHeaders() throws Exception {
        mockMvc.perform(get("/emps/export").header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX_CONTENT_TYPE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("filename*=utf-8''")));
    }

    @Test
    @DisplayName("6. 导出接口不带 token：401（和别人一样受保护）")
    void exportShouldRequireToken() throws Exception {
        mockMvc.perform(get("/emps/export"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------

    /** 发请求拿响应体的原始字节，顺带断言状态码和内容类型 */
    private byte[] download(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", XLSX_CONTENT_TYPE))
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** 把 xlsx 字节流解析成「行的列表」，每行是「单元格文本的列表」 */
    private List<List<String>> readXlsx(byte[] bytes) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        // DataFormatter 会把任意单元格类型都转成字符串，
        // 不用自己判断「这个是数字还是文本」
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
