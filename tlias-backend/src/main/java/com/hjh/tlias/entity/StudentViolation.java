package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学员违纪记录。对应 student_violation 表。
 *
 * 这张表是「事实」，student 表上的 violation_count / violation_score
 * 是它的汇总（缓存）。每次新增一条违纪，两个汇总字段必须同事务跟上——
 * 这就是 P5c 的核心业务规则。
 */
@Data
@TableName("student_violation")
public class StudentViolation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    /** 本次扣分，正整数 */
    private Integer point;

    /** 违纪事由 */
    private String description;

    private LocalDateTime createTime;

}
