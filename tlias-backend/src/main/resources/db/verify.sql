-- ============================================================================
--  tlias 建库结果自检脚本
--
--  执行完 schema.sql 和 data.sql 之后跑这个文件，把输出整段贴回来即可。
--  每一段都标了「期望值」，对不上就说明有问题。
--
--  ⚠️ 必须带字符集参数，否则中文显示成乱码：
--     mysql -uroot -p --default-character-set=utf8mb4
--
--  ⚠️ 最后两段（13、14）是「故意插错误数据」来验证约束真的生效，
--     它们必然报错。所以放在最后——因为 mysql 客户端在批处理模式下
--     一遇到错误就会中断，放前面会导致后面的正常检查全部跑不到。
-- ============================================================================

USE tlias;

SELECT '========== 1. 表数量：期望 10 ==========' AS section;
SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'tlias';

SELECT '========== 2. 各表行数 ==========' AS section;
SELECT 'dept 部门' AS tbl, COUNT(*) AS rows_cnt FROM dept
UNION ALL SELECT 'emp 员工', COUNT(*) FROM emp
UNION ALL SELECT 'emp_experience 工作经历', COUNT(*) FROM emp_experience
UNION ALL SELECT 'course 课程', COUNT(*) FROM course
UNION ALL SELECT 'clazz 班级', COUNT(*) FROM clazz
UNION ALL SELECT 'student 学员', COUNT(*) FROM student
UNION ALL SELECT 'student_violation 违纪', COUNT(*) FROM student_violation
UNION ALL SELECT 'student_score 成绩', COUNT(*) FROM student_score
UNION ALL SELECT 'oper_log 操作日志', COUNT(*) FROM oper_log
UNION ALL SELECT 'ai_call_log AI日志', COUNT(*) FROM ai_call_log;
-- 期望：6 / 15 / 18 / 8 / 6 / 48 / 32 / 120 / 0 / 0
-- 最后两张表刻意留空，由 P7 的 AOP 和 P8 的 AI 调用在运行时写入

SELECT '========== 3. 中文是否正常显示（不该是乱码）==========' AS section;
SELECT id, name FROM dept ORDER BY id;

SELECT '========== 4. 字符集：期望每行都是 utf8mb4 ==========' AS section;
SELECT table_name, table_collation
FROM information_schema.tables
WHERE table_schema = 'tlias'
ORDER BY table_name;

SELECT '========== 5. 外键是否真的建上了：期望 7 条 ==========' AS section;
SELECT rc.constraint_name,
       rc.table_name            AS child_table,
       rc.referenced_table_name AS parent_table,
       rc.delete_rule
FROM information_schema.referential_constraints rc
WHERE rc.constraint_schema = 'tlias'
ORDER BY rc.table_name, rc.constraint_name;
-- 期望的 delete_rule 分布：
--   fk_expr_emp / fk_vio_student / fk_score_student  -> CASCADE
--   fk_clazz_master                                  -> SET NULL
--   fk_emp_dept / fk_student_clazz / fk_score_course -> NO ACTION

SELECT '========== 6. 唯一键是否真的建上了：期望 6 条 ==========' AS section;
SELECT table_name, index_name,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols
FROM information_schema.statistics
WHERE table_schema = 'tlias' AND non_unique = 0 AND index_name <> 'PRIMARY'
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
-- 期望：uk_dept_name / uk_emp_username / uk_course_no / uk_clazz_name /
--       uk_student_no / uk_score_stu_course_date

SELECT '========== 7. 违纪汇总字段与明细是否一致：期望返回 0 行 ==========' AS section;
SELECT s.id, s.name, s.no,
       s.violation_count         AS stored_count,
       COUNT(v.id)               AS actual_count,
       s.violation_score         AS stored_score,
       COALESCE(SUM(v.point), 0) AS actual_score
FROM student s
         LEFT JOIN student_violation v ON v.student_id = s.id
GROUP BY s.id, s.name, s.no, s.violation_count, s.violation_score
HAVING s.violation_count <> COUNT(v.id)
    OR s.violation_score <> COALESCE(SUM(v.point), 0);
-- 这段比对「student 表里存着的汇总数字」与「从 student_violation 现算的数字」。
-- 返回空结果集 = 一致 = 通过；返回任何一行都说明回填失败。

SELECT '========== 8. 班级概况（含班主任姓名与人数）==========' AS section;
SELECT c.id,
       c.name      AS clazz_name,
       e.name      AS master_name,
       c.subject,
       c.status,
       COUNT(s.id) AS student_count
FROM clazz c
         LEFT JOIN emp e ON c.master_id = e.id
         LEFT JOIN student s ON s.clazz_id = c.id
GROUP BY c.id, c.name, e.name, c.subject, c.status
ORDER BY c.id;
-- 期望 6 行，master_name 全都不为 NULL，每班 student_count = 8

SELECT '========== 9. 违纪扣分 TOP 5（P8 智能建议会用到这批人）==========' AS section;
SELECT s.id, s.name, s.no, c.name AS clazz_name,
       s.violation_count, s.violation_score
FROM student s
         LEFT JOIN clazz c ON s.clazz_id = c.id
ORDER BY s.violation_score DESC, s.violation_count DESC
LIMIT 5;

SELECT '========== 10. 成绩概况：每门课的平均分与人数 ==========' AS section;
SELECT co.id, co.name AS course_name,
       COUNT(sc.id)          AS score_count,
       ROUND(AVG(sc.score), 2) AS avg_score,
       MIN(sc.score)         AS min_score,
       MAX(sc.score)         AS max_score
FROM course co
         LEFT JOIN student_score sc ON sc.course_id = co.id
GROUP BY co.id, co.name
ORDER BY co.id;

SELECT '========== 11. 员工登录凭据抽查：必须是密文不是明文 ==========' AS section;
SELECT id, username, name, job, dept_id, status,
       LEFT(password, 7)     AS hash_prefix,
       CHAR_LENGTH(password) AS hash_len
FROM emp
ORDER BY id
LIMIT 5;
-- 期望：hash_prefix 全是 '$2a$10$'，hash_len 全是 60。
-- 如果看到 '123456'，说明存的是明文，那是严重错误。

SELECT '========== 12. 一对多抽查：员工及其工作经历条数 ==========' AS section;
SELECT e.id, e.username, e.name, COUNT(x.id) AS experience_count
FROM emp e
         LEFT JOIN emp_experience x ON x.emp_id = e.id
GROUP BY e.id, e.username, e.name
ORDER BY e.id;
-- 期望 15 行，experience_count 出现 0 / 1 / 2 三种取值，合计 18

-- ============================================================================
--  以下两段会「故意报错」。看到 ERROR 就是通过了，不是失败。
-- ============================================================================

SELECT '========== 13. 唯一键能拦住重复吗：期望 ERROR 1062 ==========' AS section;
-- 故意插入一个已存在的学号 202601001，应当被 uk_student_no 拦下：
--   ERROR 1062 (23000): Duplicate entry '202601001' for key 'student.uk_student_no'
-- 报错 = 通过。若它插入成功了，说明唯一键没建上。
INSERT INTO student (name, `no`, gender, degree, clazz_id)
VALUES ('唯一键测试', '202601001', 1, 4, 1);

SELECT '========== 14. 外键能拦住悬空引用吗：期望 ERROR 1452 ==========' AS section;
-- 故意插入一条指向不存在班级（clazz_id=999）的学员，应当报：
--   ERROR 1452 (23000): Cannot add or update a child row: a foreign key constraint fails
INSERT INTO student (name, `no`, gender, degree, clazz_id)
VALUES ('外键测试', 'ZZZ99999999', 1, 4, 999);
