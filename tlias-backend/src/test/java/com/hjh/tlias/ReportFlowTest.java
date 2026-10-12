package com.hjh.tlias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjh.tlias.config.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * P6 数据统计的验收测试（先写、先跑红，再实现）。
 *
 * 分三批与实现交替推进：
 *   批次一：班级人数 / 学历 / 性别（本批）
 *   批次二：课程成绩 / 分数段
 *   批次三：班级违纪 / 违纪 Top10 / 部门
 *
 * 所有用例在 @Transactional 里构造数据、跑完自动回滚，库里零残留。
 * 关键断言用 JdbcTemplate 写「独立 SQL」对账——防止实现与断言同源同错。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportFlowTest {

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

    @Autowired
    private SqlSessionTemplate sqlSessionTemplate;

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
    //  批次一：班级人数 / 学历 / 性别
    // ------------------------------------------------------------------

    @Test
    @DisplayName("1. 班级人数：6 行、男+女=总、总和 48、首行是 JavaEE 就业 1 期")
    void clazzStudentCountSixRows() throws Exception {
        JsonNode rows = getData("/report/clazz-student-count");

        System.out.println("\n[1] 班级人数 = " + rows);
        assertEquals(6, rows.size());
        int sum = 0;
        for (JsonNode row : rows) {
            assertEquals(row.path("total").asInt(),
                    row.path("maleCount").asInt() + row.path("femaleCount").asInt(),
                    "每个班：男 + 女 = 总");
            sum += row.path("total").asInt();
        }
        assertEquals(48, sum);
        assertEquals("JavaEE 就业 1 期", rows.get(0).path("clazzName").asText());
    }

    @Test
    @DisplayName("2. 班级人数与库内直查一致（独立对账）")
    void clazzStudentCountMatchesDb() throws Exception {
        JsonNode rows = getData("/report/clazz-student-count");

        for (JsonNode row : rows) {
            Integer dbCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM student s JOIN clazz c ON s.clazz_id = c.id WHERE c.name = ?",
                    Integer.class, row.path("clazzName").asText());
            assertEquals(dbCount, row.path("total").asInt(),
                    "班级 " + row.path("clazzName").asText() + " 人数对不上");
        }
    }

    @Test
    @DisplayName("3. 新建空班级 → 出现且为 0（不丢行）")
    void emptyClazzAppearsWithZero() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO clazz (name, begin_date, end_date, subject, status) VALUES (?, ?, ?, ?, ?)",
                "报表空班", "2026-09-01", "2027-06-30", 1, 1);

        JsonNode rows = getData("/report/clazz-student-count");

        assertEquals(7, rows.size(), "新班级必须出现，不能被 LEFT JOIN 抹掉");
        JsonNode last = rows.get(6);   // ORDER BY c.id：新建的 id 最大，排在最后
        System.out.println("\n[3] 空班级行 = " + last);
        assertEquals("报表空班", last.path("clazzName").asText());
        assertEquals(0, last.path("total").asInt());
        assertEquals(0, last.path("maleCount").asInt());
        assertEquals(0, last.path("femaleCount").asInt());
    }

    @Test
    @DisplayName("4. 调班：1 名学员换班 → 两班人数各 ∓1")
    void transferStudentShiftsCounts() throws Exception {
        Integer fromClazzId = jdbcTemplate.queryForObject("SELECT clazz_id FROM student WHERE id = 1", Integer.class);
        String fromName = jdbcTemplate.queryForObject("SELECT name FROM clazz WHERE id = ?", String.class, fromClazzId);
        String toName = "JavaEE 就业 2 期";

        JsonNode before = getData("/report/clazz-student-count");
        int fromBefore = totalOf(before, fromName);
        int toBefore = totalOf(before, toName);

        jdbcTemplate.update("UPDATE student SET clazz_id = (SELECT id FROM clazz WHERE name = ?) WHERE id = 1", toName);
        clearMyBatisCache();   // JdbcTemplate 绕过 MyBatis 写库，一级缓存不失效，不清则下面重读是旧值

        JsonNode after = getData("/report/clazz-student-count");
        System.out.println("\n[4] " + fromName + ": " + fromBefore + " → " + totalOf(after, fromName)
                + "；" + toName + ": " + toBefore + " → " + totalOf(after, toName));
        assertEquals(fromBefore - 1, totalOf(after, fromName));
        assertEquals(toBefore + 1, totalOf(after, toName));
    }

    @Test
    @DisplayName("5. 学历分布：6 类全出、按编码升序、求和 48")
    void studentDegreeSixRows() throws Exception {
        JsonNode rows = getData("/report/student-degree");

        System.out.println("\n[5] 学历分布 = " + rows);
        assertEquals(6, rows.size());
        int sum = 0;
        for (int i = 0; i < 6; i++) {
            assertEquals(i + 1, rows.get(i).path("degree").asInt());
            sum += rows.get(i).path("count").asInt();
        }
        assertEquals(48, sum);
        assertEquals("初中", rows.get(0).path("degreeName").asText());
        assertEquals("博士", rows.get(5).path("degreeName").asText());
    }

    @Test
    @DisplayName("6. 学历分布与库内直查一致（独立对账）")
    void studentDegreeMatchesDb() throws Exception {
        JsonNode rows = getData("/report/student-degree");

        for (JsonNode row : rows) {
            Integer dbCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM student WHERE degree = ?", Integer.class, row.path("degree").asInt());
            assertEquals(dbCount, row.path("count").asInt());
        }
    }

    @Test
    @DisplayName("7. 性别分布：2 行、男+女=48、名字正确")
    void studentGenderTwoRows() throws Exception {
        JsonNode rows = getData("/report/student-gender");

        System.out.println("\n[7] 性别分布 = " + rows);
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).path("gender").asInt());
        assertEquals("男", rows.get(0).path("genderName").asText());
        assertEquals(2, rows.get(1).path("gender").asInt());
        assertEquals("女", rows.get(1).path("genderName").asText());
        assertEquals(48, rows.get(0).path("count").asInt() + rows.get(1).path("count").asInt());
    }

    @Test
    @DisplayName("8. 未登录访问统计接口 → 401")
    void reportRequiresAuth() throws Exception {
        mockMvc.perform(get("/report/clazz-student-count")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  批次二：课程成绩 / 分数段
    // ------------------------------------------------------------------

    @Test
    @DisplayName("9. 课程成绩：8 行全出（以 course 为基准，不许丢行）")
    void courseScoreEightRows() throws Exception {
        JsonNode rows = getData("/report/course-score");

        assertEquals(8, rows.size());
        for (JsonNode row : rows) {
            assertTrue(row.path("courseName").asText().length() > 0, "课程名不能为空");
        }
    }

    @Test
    @DisplayName("10. 课程成绩与库内直查一致：人次 / 平均 / 最高 / 最低")
    void courseScoreMatchesDb() throws Exception {
        JsonNode rows = getData("/report/course-score");

        for (JsonNode row : rows) {
            String courseName = row.path("courseName").asText();
            Map<String, Object> db = jdbcTemplate.queryForMap(
                    "SELECT COUNT(ss.id) AS examCount, ROUND(AVG(ss.score),2) AS avgScore, " +
                    "MAX(ss.score) AS maxScore, MIN(ss.score) AS minScore " +
                    "FROM course c LEFT JOIN student_score ss ON ss.course_id = c.id " +
                    "WHERE c.name = ? GROUP BY c.id", courseName);

            assertEquals(((Number) db.get("examCount")).intValue(), row.path("examCount").asInt(),
                    courseName + " 考试人次对不上");
            assertDecimalEquals(db.get("avgScore"), row.path("avgScore"), courseName + " 平均分");
            assertDecimalEquals(db.get("maxScore"), row.path("maxScore"), courseName + " 最高分");
            assertDecimalEquals(db.get("minScore"), row.path("minScore"), courseName + " 最低分");
        }
    }

    @Test
    @DisplayName("11. 及格率边界：59.99 不计、60.00 计（新课程构造）")
    void passRateBoundary() throws Exception {
        createCourse("报表边界课", "RPT001");
        Long courseId = jdbcTemplate.queryForObject("SELECT id FROM course WHERE name = '报表边界课'", Long.class);

        jdbcTemplate.update("INSERT INTO student_score (student_id, course_id, exam_date, score) VALUES (1, ?, '2030-01-01', 59.99)", courseId);
        jdbcTemplate.update("INSERT INTO student_score (student_id, course_id, exam_date, score) VALUES (2, ?, '2030-01-01', 60.00)", courseId);

        JsonNode row = findRow(getData("/report/course-score"), "courseName", "报表边界课");

        System.out.println("\n[11] 边界课 = " + row);
        assertEquals(2, row.path("examCount").asInt());
        assertDecimalEquals(new BigDecimal("50.0"), row.path("passRate"), "2 人次中恰好 1 人次及格 → 50.0");
    }

    @Test
    @DisplayName("12. 分数段：5 桶全出、求和 120、边界归桶正确")
    void scoreDistributionBuckets() throws Exception {
        JsonNode before = getData("/report/score-distribution");

        assertEquals(5, before.size());
        assertEquals("0-59", before.get(0).path("range").asText());
        assertEquals("60-69", before.get(1).path("range").asText());
        assertEquals("70-79", before.get(2).path("range").asText());
        assertEquals("80-89", before.get(3).path("range").asText());
        assertEquals("90-100", before.get(4).path("range").asText());
        int sum = 0;
        for (JsonNode row : before) {
            sum += row.path("count").asInt();
        }
        assertEquals(120, sum);

        // 构造三条边界分（新课程，避开唯一的 (student,course,date) 约束）
        createCourse("报表分桶课", "RPT002");
        Long courseId = jdbcTemplate.queryForObject("SELECT id FROM course WHERE name = '报表分桶课'", Long.class);
        jdbcTemplate.update("INSERT INTO student_score (student_id, course_id, exam_date, score) VALUES (1, ?, '2030-01-01', 59.99)", courseId);
        jdbcTemplate.update("INSERT INTO student_score (student_id, course_id, exam_date, score) VALUES (2, ?, '2030-01-01', 60.00)", courseId);
        jdbcTemplate.update("INSERT INTO student_score (student_id, course_id, exam_date, score) VALUES (3, ?, '2030-01-01', 90.00)", courseId);
        clearMyBatisCache();   // 外部写后重读同一语句：不清一级缓存会拿到旧快照

        JsonNode after = getData("/report/score-distribution");
        assertEquals(1, countOfRange(after, "0-59") - countOfRange(before, "0-59"), "59.99 应落 0-59");
        assertEquals(1, countOfRange(after, "60-69") - countOfRange(before, "60-69"), "60.00 应落 60-69");
        assertEquals(1, countOfRange(after, "90-100") - countOfRange(before, "90-100"), "90.00 应落 90-100");
    }

    @Test
    @DisplayName("13. 无成绩的新课程：examCount=0、分值为 null、行不丢")
    void courseWithoutScoreKeepsRow() throws Exception {
        createCourse("报表无分课", "RPT003");

        JsonNode rows = getData("/report/course-score");

        assertEquals(9, rows.size());
        JsonNode row = findRow(rows, "courseName", "报表无分课");
        System.out.println("\n[13] 无分课程行 = " + row);
        assertEquals(0, row.path("examCount").asInt());
        assertJsonNull(row.get("avgScore"), "avgScore");
        assertJsonNull(row.get("passRate"), "passRate");
    }

    // ------------------------------------------------------------------
    //  批次三：班级违纪 / 违纪 Top10 / 部门
    // ------------------------------------------------------------------

    @Test
    @DisplayName("14. 班级违纪：逐班对账 + 全局求和")
    void clazzViolationMatchesDb() throws Exception {
        JsonNode rows = getData("/report/clazz-violation");

        System.out.println("\n[14] 班级违纪 = " + rows);
        assertEquals(6, rows.size());
        int pointsSum = 0;
        int countSum = 0;
        for (JsonNode row : rows) {
            Map<String, Object> db = jdbcTemplate.queryForMap(
                    "SELECT COUNT(v.id) AS cnt, COALESCE(SUM(v.point), 0) AS pts " +
                    "FROM clazz c LEFT JOIN student s ON s.clazz_id = c.id " +
                    "LEFT JOIN student_violation v ON v.student_id = s.id " +
                    "WHERE c.name = ? GROUP BY c.id", row.path("clazzName").asText());

            assertEquals(((Number) db.get("cnt")).intValue(), row.path("violationCount").asInt(),
                    row.path("clazzName").asText() + " 违纪人次对不上");
            assertEquals(((Number) db.get("pts")).intValue(), row.path("totalPoints").asInt(),
                    row.path("clazzName").asText() + " 总扣分对不上");
            pointsSum += row.path("totalPoints").asInt();
            countSum += row.path("violationCount").asInt();
        }

        Integer dbPoints = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(point), 0) FROM student_violation", Integer.class);
        Integer dbCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student_violation", Integer.class);
        assertEquals(dbPoints, pointsSum, "各班求和应等于明细表全表 SUM");
        assertEquals(dbCount, countSum);
    }

    @Test
    @DisplayName("15. 清掉某班全部违纪 → 该班以 0 出现（不丢行）")
    void clazzWithoutViolationAppearsZero() throws Exception {
        // 动态挑一个「违纪最多」的班做构造对象，不猜种子数据
        Long clazzId = jdbcTemplate.queryForObject(
                "SELECT s.clazz_id FROM student_violation v JOIN student s ON v.student_id = s.id " +
                "WHERE s.clazz_id IS NOT NULL GROUP BY s.clazz_id ORDER BY COUNT(*) DESC LIMIT 1", Long.class);
        String clazzName = jdbcTemplate.queryForObject("SELECT name FROM clazz WHERE id = ?", String.class, clazzId);

        int deleted = jdbcTemplate.update(
                "DELETE FROM student_violation WHERE student_id IN (SELECT id FROM student WHERE clazz_id = ?)", clazzId);
        assertTrue(deleted > 0, "构造前提：这个班本来有违纪");

        JsonNode rows = getData("/report/clazz-violation");
        assertEquals(6, rows.size(), "班级行数不许变");
        JsonNode row = findRow(rows, "clazzName", clazzName);
        System.out.println("\n[15] 清零后的班 = " + row);
        assertEquals(0, row.path("violationCount").asInt());
        assertEquals(0, row.path("totalPoints").asInt());
    }

    @Test
    @DisplayName("16. 违纪 Top10：≤10 条、扣分降序、与直查榜首一致、重复查询稳定")
    void violationTopOrdered() throws Exception {
        JsonNode rows = getData("/report/violation-top");

        assertTrue(rows.size() > 0);
        assertTrue(rows.size() <= 10);
        System.out.println("\n[16] Top10 榜首 = " + rows.get(0));

        int lastScore = Integer.MAX_VALUE;
        int lastCount = Integer.MAX_VALUE;
        for (JsonNode row : rows) {
            int score = row.path("violationScore").asInt();
            int count = row.path("violationCount").asInt();
            assertTrue(score < lastScore || (score == lastScore && count <= lastCount),
                    "应稳定降序：扣分 → 次数");
            lastScore = score;
            lastCount = count;
        }

        Map<String, Object> top = jdbcTemplate.queryForMap(
                "SELECT s.name AS studentName, s.violation_score AS vScore FROM student s " +
                "WHERE s.violation_count > 0 " +
                "ORDER BY s.violation_score DESC, s.violation_count DESC, s.id ASC LIMIT 1");
        assertEquals(top.get("studentName").toString(), rows.get(0).path("studentName").asText());
        assertEquals(((Number) top.get("vScore")).intValue(), rows.get(0).path("violationScore").asInt());

        // 同一份数据查两次顺序必须一致（稳定排序）。
        // 两次读之间清一次缓存，让两边都真正打到数据库——不清就是缓存自己跟自己比，等于没测。
        JsonNode first = getData("/report/violation-top");
        clearMyBatisCache();
        JsonNode second = getData("/report/violation-top");
        assertEquals(first.toString(), second.toString());
    }

    @Test
    @DisplayName("17. Top10 不含无违纪学员")
    void violationTopExcludesClean() throws Exception {
        JsonNode rows = getData("/report/violation-top");

        // 用「名字集合」判断（防同名学员导致按名字单查撞多行）
        List<String> dirtyNames = jdbcTemplate.queryForList(
                "SELECT name FROM student WHERE violation_count > 0", String.class);
        for (JsonNode row : rows) {
            assertTrue(dirtyNames.contains(row.path("studentName").asText()),
                    row.path("studentName").asText() + " 不在违纪名单里，不该出现在榜单");
        }
    }

    @Test
    @DisplayName("18. 部门统计：6 部门全出、在职+离职=总、总和 15")
    void deptEmpCount() throws Exception {
        JsonNode rows = getData("/report/dept-emp-count");

        System.out.println("\n[18] 部门人数 = " + rows);
        assertEquals(6, rows.size());
        int sum = 0;
        for (JsonNode row : rows) {
            assertEquals(row.path("total").asInt(),
                    row.path("activeCount").asInt() + row.path("inactiveCount").asInt());
            sum += row.path("total").asInt();
        }
        assertEquals(15, sum);

        Integer db = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM emp e JOIN dept d ON e.dept_id = d.id WHERE d.name = ?",
                Integer.class, rows.get(0).path("deptName").asText());
        assertEquals(db, rows.get(0).path("total").asInt());
    }

    @Test
    @DisplayName("19. 新建 0 人部门 → 出现且为 0（不丢行）")
    void emptyDeptAppearsZero() throws Exception {
        jdbcTemplate.update("INSERT INTO dept (name) VALUES (?)", "报表空部");

        JsonNode rows = getData("/report/dept-emp-count");
        assertEquals(7, rows.size());
        JsonNode last = rows.get(6);   // ORDER BY d.id：新建的排在最后
        System.out.println("\n[19] 空部门行 = " + last);
        assertEquals("报表空部", last.path("deptName").asText());
        assertEquals(0, last.path("total").asInt());
    }

    @Test
    @DisplayName("20. 未登录访问违纪 Top → 401")
    void violationTopRequiresAuth() throws Exception {
        mockMvc.perform(get("/report/violation-top")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    //  测试自己的小工具
    // ------------------------------------------------------------------

    /** 发 GET 并返回 data 节点（要求 code=1） */
    private JsonNode getData(String uri) throws Exception {
        String body = mockMvc.perform(get(uri).header(jwtProperties.getTokenName(), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).path("data");
    }

    /**
     * 清 MyBatis 一级缓存。
     *
     * @Transactional 测试里 MockMvc 与 JdbcTemplate 共用连接（所以 JdbcTemplate 的写对接口可见），
     * 但 MyBatis 的一级缓存挂在 SqlSession 上，只在「走 MyBatis 的写」时才自动失效；
     * JdbcTemplate 这种外部写之后，同一语句的重复读会命中旧快照——必须手动清掉，
     * 让重读真正落到数据库（等价于真实运行时每个请求独立会话的语义）。
     */
    private void clearMyBatisCache() {
        sqlSessionTemplate.clearCache();
    }

    /** 在名单里按名字字段找一行；找不到直接失败（比空指针好排查） */
    private JsonNode findRow(JsonNode rows, String nameField, String name) {
        for (JsonNode row : rows) {
            if (name.equals(row.path(nameField).asText())) {
                return row;
            }
        }
        throw new AssertionError("结果里找不到 " + nameField + " = " + name);
    }

    private int totalOf(JsonNode rows, String clazzName) {
        return findRow(rows, "clazzName", clazzName).path("total").asInt();
    }

    /** 插一门新课程（统计用例的构造素材），价格 0，不参与任何断言 */
    private void createCourse(String name, String no) {
        jdbcTemplate.update("INSERT INTO course (name, `no`, price) VALUES (?, ?, 0)", name, no);
    }

    /** 与库内直查值对账：用 compareTo 避开 50.0 与 50.00 的 scale 差异；库中为 null 时要求接口也 null */
    private void assertDecimalEquals(Object dbValue, JsonNode json, String message) {
        if (dbValue == null) {
            assertJsonNull(json, message);
            return;
        }
        assertEquals(0, new BigDecimal(dbValue.toString()).compareTo(json.decimalValue()), message);
    }

    private void assertJsonNull(JsonNode node, String label) {
        assertTrue(node == null || node.isNull(), label + " 应为 null");
    }

    private int countOfRange(JsonNode rows, String range) {
        return findRow(rows, "range", range).path("count").asInt();
    }

}
