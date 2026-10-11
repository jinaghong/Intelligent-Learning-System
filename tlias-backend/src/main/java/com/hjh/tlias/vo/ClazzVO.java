package com.hjh.tlias.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 班级列表 / 详情里的一条。
 *
 * 和 Clazz 实体的区别：多了 masterName。
 * clazz 表里只存了 master_id（员工的 id），班主任姓名是靠 LEFT JOIN emp
 * 从另一张表带出来的——所以它属于「多表查询的结果」，由 VO 承载，
 * 不往实体上硬塞（实体是 clazz 表的镜像，塞进去就是张冠李戴）。
 */
@Data
public class ClazzVO {

    private Long id;

    private String name;

    private String room;

    private LocalDate beginDate;

    private LocalDate endDate;

    private Long masterId;

    /** 关联 emp 表查出来的班主任姓名，可能为 null（该班暂时没有班主任） */
    private String masterName;

    /** 学科：1Java 2前端 3大数据 4Python 5测试 */
    private Integer subject;

    /** 状态：1在读 2结课 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
