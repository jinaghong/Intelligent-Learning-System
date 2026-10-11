package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程。对应 course 表，是最简单的一张业务表：没有外键、没有从属数据，
 * 只有「编号唯一」一条约束。
 */
@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 课程编号（C001 这种），库里有唯一键 uk_course_no。
     * MySQL 里 no 是关键字但非保留字，当列名可以不用反引号——schema.sql 里
     * 加反引号只是防御性写法，MyBatis-Plus 生成的 SQL 用 no 不加引号也能跑。
     */
    private String no;

    /**
     * 课程价格。
     *
     * 金额为什么用 BigDecimal 不用 double：double 是二进制浮点数，
     * 0.1 + 0.2 会等于 0.30000000000000004。价格算错一分钱都是事故，
     * 所以数据库那列是 DECIMAL，这里也必须用能精确表示十进制的 BigDecimal。
     */
    private BigDecimal price;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
