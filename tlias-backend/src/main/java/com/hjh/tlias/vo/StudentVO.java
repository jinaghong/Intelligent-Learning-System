package com.hjh.tlias.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学员列表里的一行。
 *
 * 和 Student 实体的区别：多了 clazzName（LEFT JOIN clazz 带出来的），
 * 不含 id_card / address 这类列表上不展示的隐私字段——少查一列少一分泄露面。
 */
@Data
public class StudentVO {

    private Long id;

    private String name;

    private String no;

    /** 1男 2女 */
    private Integer gender;

    private String phone;

    /** 学历：1初中 ~ 6博士 */
    private Integer degree;

    private Long clazzId;

    /** 关联 clazz 表查出来的班级名，可能为 null（没分班） */
    private String clazzName;

    /** 是否院校合作生源：0否 1是 */
    private Integer isCollege;

    private Integer violationCount;

    private Integer violationScore;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
