package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学员。对应 student 表。
 *
 * violationCount / violationScore 是**冗余汇总字段**：不手写，由
 * student_violation 表的记录驱动（P5c 的违纪处理，同事务累加）。
 * 建表时就定下了这个口径——明细是事实，汇总是缓存；两边不一致时以明细为准。
 */
@Data
@TableName("student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 学号，库里有唯一键 uk_student_no */
    private String no;

    /** 1男 2女 */
    private Integer gender;

    private String phone;

    /** 学历：1初中 2高中 3专科 4本科 5硕士 6博士 */
    private Integer degree;

    /** 所属班级，外键指向 clazz.id；可以为 null（还没分班） */
    private Long clazzId;

    /** 身份证号，固定 18 位 */
    private String idCard;

    /** 是否院校合作生源：0否 1是 */
    private Integer isCollege;

    private String address;

    private LocalDate graduationDate;

    /** 违纪次数，由 student_violation 汇总而来 */
    private Integer violationCount;

    /** 累计违纪扣分，由 student_violation 汇总而来 */
    private Integer violationScore;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
