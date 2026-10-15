-- ============================================================================
--  tlias 智能学习辅助系统 · 建库建表脚本
--
--  ⚠️ 警告：本脚本会 DROP 掉 tlias 库里的同名表，表内数据全部丢失。
--     首次执行没有影响；重复执行等于清空重建。生产环境绝不这么做。
--
--  执行方式（在项目里 source 本文件即可，不需要提前 USE）：
--     mysql -uroot -p
--     mysql> source D:/qoder/Inteligent-Learning-System/tlias-backend/src/main/resources/db/schema.sql
--
--  建表顺序按外键依赖排列：先建被引用的父表，再建引用它的子表。
--  删表顺序正好相反：先删子表，否则外键约束会阻止删除父表。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS tlias
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE tlias;

-- ---------------------------------------------------------------------------
-- 删表（子表在前，父表在后）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS ai_call_log;
DROP TABLE IF EXISTS oper_log;
DROP TABLE IF EXISTS student_score;
DROP TABLE IF EXISTS student_violation;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS clazz;
DROP TABLE IF EXISTS emp_experience;
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS emp;
DROP TABLE IF EXISTS dept;

-- ---------------------------------------------------------------------------
-- 1. dept 部门
-- ---------------------------------------------------------------------------
CREATE TABLE dept (
    id          INT         NOT NULL AUTO_INCREMENT COMMENT '部门ID，主键',
    name        VARCHAR(30) NOT NULL COMMENT '部门名称',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间，插入时自动填',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间，每次 UPDATE 自动刷新',
    PRIMARY KEY (id),
    -- 唯一键。它同时承担两个职责：防重名，以及给 name 列建索引（唯一索引也是索引）
    UNIQUE KEY uk_dept_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='部门表';

-- ---------------------------------------------------------------------------
-- 2. emp 员工（登录就用这张表）
-- ---------------------------------------------------------------------------
CREATE TABLE emp (
    id          INT          NOT NULL AUTO_INCREMENT COMMENT '员工ID，主键',
    username    VARCHAR(20)  NOT NULL COMMENT '登录用户名',
    password    VARCHAR(100) NOT NULL COMMENT '登录密码，存 BCrypt 密文（固定60字符），绝不存明文',
    name        VARCHAR(20)  NOT NULL COMMENT '姓名',
    gender      TINYINT      NOT NULL COMMENT '性别：1男 2女',
    image       VARCHAR(300)          DEFAULT NULL COMMENT '头像图片的访问URL',
    job         TINYINT      NOT NULL COMMENT '职位：1班主任 2讲师 3学工主管 4教研主管 5咨询师',
    entry_date  DATE         NOT NULL COMMENT '入职日期',
    dept_id     INT                   DEFAULT NULL COMMENT '所属部门ID，外键指向 dept.id',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1在职 2离职',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_emp_username (username),
    KEY idx_emp_dept (dept_id),
    KEY idx_emp_entry_date (entry_date),
    KEY idx_emp_name (name),
    -- 不给 ON DELETE：默认 RESTRICT，即「部门下还有员工时删不掉部门」。
    -- 但业务上不依赖它报错，而是在 Service 层先查一次并返回友好错误码
    CONSTRAINT fk_emp_dept FOREIGN KEY (dept_id) REFERENCES dept (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='员工表';

-- ---------------------------------------------------------------------------
-- 3. emp_experience 员工工作经历（一对多的「多」方）
-- ---------------------------------------------------------------------------
CREATE TABLE emp_experience (
    id         INT         NOT NULL AUTO_INCREMENT COMMENT '经历ID，主键',
    emp_id     INT         NOT NULL COMMENT '所属员工ID，外键指向 emp.id',
    begin_date DATE        NOT NULL COMMENT '开始时间',
    end_date   DATE        NOT NULL COMMENT '结束时间',
    company    VARCHAR(50) NOT NULL COMMENT '公司名称',
    job        VARCHAR(20) NOT NULL COMMENT '担任职位',
    PRIMARY KEY (id),
    KEY idx_expr_emp (emp_id),
    -- ON DELETE CASCADE：删员工时，他的工作经历自动一起删，不留孤儿数据
    CONSTRAINT fk_expr_emp FOREIGN KEY (emp_id) REFERENCES emp (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='员工工作经历表';

-- ---------------------------------------------------------------------------
-- 4. course 课程
-- ---------------------------------------------------------------------------
CREATE TABLE course (
    id          INT           NOT NULL AUTO_INCREMENT COMMENT '课程ID，主键',
    name        VARCHAR(50)   NOT NULL COMMENT '课程名称',
    `no`        VARCHAR(20)   NOT NULL COMMENT '课程编号。no 是 MySQL 关键字，必须用反引号包起来',
    price       DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '课程价格。金额一律用 DECIMAL，不用 FLOAT/DOUBLE',
    description VARCHAR(500)           DEFAULT NULL COMMENT '课程简介',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_no (`no`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='课程表';

-- ---------------------------------------------------------------------------
-- 5. clazz 班级（依赖 emp，因为班主任是员工）
-- ---------------------------------------------------------------------------
CREATE TABLE clazz (
    id          INT         NOT NULL AUTO_INCREMENT COMMENT '班级ID，主键',
    name        VARCHAR(30) NOT NULL COMMENT '班级名称',
    room        VARCHAR(20)          DEFAULT NULL COMMENT '上课教室',
    begin_date  DATE        NOT NULL COMMENT '开班日期',
    end_date    DATE        NOT NULL COMMENT '结班日期',
    master_id   INT                  DEFAULT NULL COMMENT '班主任ID，外键指向 emp.id',
    subject     TINYINT     NOT NULL COMMENT '学科：1Java 2前端 3大数据 4Python 5测试',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1在读 2结课',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_clazz_name (name),
    KEY idx_clazz_master (master_id),
    KEY idx_clazz_subject (subject),
    -- ON DELETE SET NULL：删掉某个老师时，他带的班级不该跟着消失，
    -- 只是暂时变成「没有班主任」。用 RESTRICT 的话会抛外键异常，很难转成友好提示
    CONSTRAINT fk_clazz_master FOREIGN KEY (master_id) REFERENCES emp (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='班级表';

-- ---------------------------------------------------------------------------
-- 6. student 学员（依赖 clazz）
-- ---------------------------------------------------------------------------
CREATE TABLE student (
    id              INT          NOT NULL AUTO_INCREMENT COMMENT '学员ID，主键',
    name            VARCHAR(20)  NOT NULL COMMENT '姓名',
    `no`            VARCHAR(20)  NOT NULL COMMENT '学号',
    gender          TINYINT      NOT NULL COMMENT '性别：1男 2女',
    phone           VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    degree          TINYINT      NOT NULL COMMENT '学历：1初中 2高中 3专科 4本科 5硕士 6博士',
    clazz_id        INT                   DEFAULT NULL COMMENT '所属班级ID，外键指向 clazz.id',
    id_card         CHAR(18)              DEFAULT NULL COMMENT '身份证号，固定18位所以用 CHAR 而非 VARCHAR',
    is_college      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否院校合作生源：0否 1是',
    address         VARCHAR(200)          DEFAULT NULL COMMENT '家庭住址',
    graduation_date DATE                  DEFAULT NULL COMMENT '毕业时间',
    violation_count INT          NOT NULL DEFAULT 0 COMMENT '违纪次数，由 student_violation 汇总而来',
    violation_score INT          NOT NULL DEFAULT 0 COMMENT '累计违纪扣分，由 student_violation 汇总而来',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_no (`no`),
    KEY idx_student_clazz (clazz_id),
    KEY idx_student_degree (degree),
    -- RESTRICT：班级下还有学员时不许删班级。同样由 Service 层先判断并返回业务错误码
    CONSTRAINT fk_student_clazz FOREIGN KEY (clazz_id) REFERENCES clazz (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='学员表';

-- ---------------------------------------------------------------------------
-- 7. student_violation 学员违纪记录
-- ---------------------------------------------------------------------------
CREATE TABLE student_violation (
    id          INT          NOT NULL AUTO_INCREMENT COMMENT '违纪记录ID，主键',
    student_id  INT          NOT NULL COMMENT '学员ID，外键指向 student.id',
    point       INT          NOT NULL COMMENT '本次扣分',
    description VARCHAR(200) NOT NULL COMMENT '违纪事由',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    PRIMARY KEY (id),
    KEY idx_vio_student (student_id),
    CONSTRAINT fk_vio_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='学员违纪记录表';

-- ---------------------------------------------------------------------------
-- 8. student_score 学员成绩（喂给「智能学习建议」的画像数据源）
-- ---------------------------------------------------------------------------
CREATE TABLE student_score (
    id         INT          NOT NULL AUTO_INCREMENT COMMENT '成绩ID，主键',
    student_id INT          NOT NULL COMMENT '学员ID，外键指向 student.id',
    course_id  INT          NOT NULL COMMENT '课程ID，外键指向 course.id',
    exam_date  DATE         NOT NULL COMMENT '考试日期',
    score      DECIMAL(5, 2) NOT NULL COMMENT '分数，范围 0.00 - 100.00',
    PRIMARY KEY (id),
    KEY idx_score_student (student_id),
    KEY idx_score_course (course_id),
    -- 防止同一人同一门课同一天被录入两次成绩（重复提交表单是最常见的来源）
    UNIQUE KEY uk_score_stu_course_date (student_id, course_id, exam_date),
    CONSTRAINT fk_score_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
    CONSTRAINT fk_score_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='学员成绩表';

-- ---------------------------------------------------------------------------
-- 9. oper_log 操作日志（由 AOP 切面在 P7 阶段同步写入；异步方案见计划书-P7 的偏差说明）
--    刻意不建外键指向 emp：审计日志必须在当事人被删除后依然留存
-- ---------------------------------------------------------------------------
CREATE TABLE oper_log (
    id            INT          NOT NULL AUTO_INCREMENT COMMENT '日志ID，主键',
    emp_id        INT                   DEFAULT NULL COMMENT '操作人ID。不建外键，员工被删后日志仍要保留',
    operate_time  DATETIME     NOT NULL COMMENT '操作时间',
    class_name    VARCHAR(100) NOT NULL COMMENT '被调用的类名',
    method_name   VARCHAR(100) NOT NULL COMMENT '被调用的方法名',
    method_params TEXT COMMENT '方法入参，JSON 序列化后的字符串',
    return_value  TEXT COMMENT '方法返回值，JSON 序列化后的字符串',
    cost_time     BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时，单位毫秒',
    ip            VARCHAR(50)           DEFAULT NULL COMMENT '操作人IP地址',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '执行结果：1成功 0失败',
    error_msg     TEXT COMMENT '失败时的异常信息',
    PRIMARY KEY (id),
    KEY idx_log_time (operate_time),
    KEY idx_log_emp (emp_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='操作日志表（AOP 同步落库）';

-- ---------------------------------------------------------------------------
-- 10. ai_call_log AI 调用日志（可观测性 + 降级判据 + token 计费审计）
--     同样刻意不建外键
-- ---------------------------------------------------------------------------
CREATE TABLE ai_call_log (
    id                INT         NOT NULL AUTO_INCREMENT COMMENT '日志ID，主键',
    biz_type          VARCHAR(30) NOT NULL COMMENT '业务类型：ADVICE 智能学习建议 / QUESTION 智能出题',
    model             VARCHAR(50)          DEFAULT NULL COMMENT '实际调用的模型名；降级时记 FALLBACK',
    emp_id            INT                  DEFAULT NULL COMMENT '发起人ID。不建外键，理由同 oper_log',
    prompt            TEXT COMMENT '发送给模型的完整提示词',
    response          TEXT COMMENT '模型返回的完整内容',
    prompt_tokens     INT         NOT NULL DEFAULT 0 COMMENT '输入消耗的 token 数',
    completion_tokens INT         NOT NULL DEFAULT 0 COMMENT '输出消耗的 token 数',
    cost_time         BIGINT      NOT NULL DEFAULT 0 COMMENT '耗时，单位毫秒',
    status            TINYINT     NOT NULL DEFAULT 1 COMMENT '1成功 0失败或已降级',
    error_msg         TEXT COMMENT '失败或降级原因',
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (id),
    KEY idx_ai_biz (biz_type),
    KEY idx_ai_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI调用日志表';
