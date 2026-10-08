package com.hjh.tlias;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库冒烟测试。
 *
 * 它同时干两件事：
 *   1. 验证 P1a 配的数据源真的能连上 MySQL
 *   2. 顺带把 P0.5 的种子数据验证掉（表建出来了 ≠ 数据装进去了，两者要分别验证）
 *
 * 运行方式：mvn test
 * 前提：application-local.yml 里已经填好数据库密码。
 */
@SpringBootTest
class DbSmokeTest {

    private static final Map<String, Integer> EXPECTED_ROWS = new LinkedHashMap<>();

    static {
        EXPECTED_ROWS.put("dept", 6);
        EXPECTED_ROWS.put("emp", 15);
        EXPECTED_ROWS.put("emp_experience", 18);
        EXPECTED_ROWS.put("course", 8);
        EXPECTED_ROWS.put("clazz", 6);
        EXPECTED_ROWS.put("student", 48);
        EXPECTED_ROWS.put("student_violation", 32);
        EXPECTED_ROWS.put("student_score", 120);
        EXPECTED_ROWS.put("oper_log", 0);
        EXPECTED_ROWS.put("ai_call_log", 0);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void printActualDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            System.out.println();
            System.out.println("========== 实际连上的数据库 ==========");
            System.out.println("  产品   : " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
            System.out.println("  驱动   : " + md.getDriverName() + " " + md.getDriverVersion());
            System.out.println("  当前库 : " + conn.getCatalog());
            System.out.println("  URL    : " + md.getURL());
            System.out.println("=====================================");
        }
    }

    @Test
    void tableRowCountsShouldMatchSeedData() {
        Map<String, Integer> actualRows = new LinkedHashMap<>();
        EXPECTED_ROWS.keySet().forEach(table ->
                actualRows.put(table, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)));

        System.out.println();
        System.out.println("========== 各表行数 ==========");
        EXPECTED_ROWS.forEach((table, expected) -> {
            Integer actual = actualRows.get(table);
            System.out.printf("  %-18s 期望 %4d   实际 %4d   %s%n",
                    table, expected, actual, actual.equals(expected) ? "一致" : "<<< 不一致");
        });

        EXPECTED_ROWS.forEach((table, expected) ->
                assertEquals(expected, actualRows.get(table), table + " 的行数与种子数据不一致"));
    }

    @Test
    void chineseCharactersShouldNotBeGarbled() {
        List<String> deptNames = jdbcTemplate.queryForList("SELECT name FROM dept ORDER BY id", String.class);
        System.out.println();
        System.out.println("========== 部门名称（检查中文是否正常）==========");
        System.out.println("  " + String.join(" / ", deptNames));

        List<String> expected = List.of("学工部", "教研部", "咨询部", "就业部", "人事部", "财务部");
        assertEquals(expected, deptNames, "部门名称与预期不符，可能是字符集导致的乱码");
    }

    @Test
    void violationSummaryShouldMatchDetail() {
        List<Map<String, Object>> mismatched = jdbcTemplate.queryForList("""
                SELECT s.id, s.name, s.violation_count, s.violation_score
                FROM student s
                WHERE s.violation_count <> (SELECT COUNT(*) FROM student_violation v WHERE v.student_id = s.id)
                   OR s.violation_score <> (SELECT COALESCE(SUM(v.point), 0) FROM student_violation v WHERE v.student_id = s.id)
                """);

        System.out.println();
        System.out.println("========== 违纪汇总字段与明细的一致性 ==========");
        System.out.println("  不一致的学员数: " + mismatched.size() + "（期望 0）");
        mismatched.forEach(row -> System.out.println("  " + row));

        assertTrue(mismatched.isEmpty(), "有学员的 violation_count / violation_score 与明细对不上");
    }

    @Test
    void employeePasswordsShouldBeBcryptHashes() {
        List<String> hashes = new ArrayList<>(jdbcTemplate.queryForList("SELECT password FROM emp", String.class));

        System.out.println();
        System.out.println("========== 员工密码存储形式 ==========");
        System.out.println("  记录数: " + hashes.size());
        hashes.stream().limit(3).forEach(h ->
                System.out.println("  样例: " + h.substring(0, 7) + "..." + h.substring(h.length() - 6)
                        + "  长度 " + h.length()));

        assertEquals(15, hashes.size(), "员工数应为 15");
        hashes.forEach(h -> {
            assertEquals(60, h.length(), "BCrypt 密文长度应为 60，实际: " + h);
            assertTrue(h.startsWith("$2a$10$"), "BCrypt 前缀应为 $2a$10$，实际: " + h);
        });

        Set<String> distinct = new HashSet<>(hashes);
        System.out.println("  去重后: " + distinct.size() + "（等于记录数才说明每个人都用了不同的盐）");
        assertEquals(hashes.size(), distinct.size(), "密文出现重复，说明没用随机盐");
    }

}
