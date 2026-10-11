package com.hjh.tlias.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学员详情。
 *
 * 比列表的 StudentVO 多两类字段：
 *   - 敏感/长文本：身份证号、住址、毕业时间（只在详情页回显）
 *   - violationList：违纪明细，来自 student_violation 表，由 Service 查了填进去
 *     （和工作经历一样，不在这条 SQL 里 JOIN——一对多 JOIN 会把学员行撑成多行）
 */
@Data
public class StudentDetailVO {

    private Long id;

    private String name;

    private String no;

    private Integer gender;

    private String phone;

    private Integer degree;

    private Long clazzId;

    private String clazzName;

    private String idCard;

    private Integer isCollege;

    private String address;

    private LocalDate graduationDate;

    private Integer violationCount;

    private Integer violationScore;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 违纪记录，按记录时间倒序（最新的在前） */
    private List<ViolationVO> violationList;

}
