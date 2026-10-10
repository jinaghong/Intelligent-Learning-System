package com.hjh.tlias.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工列表里的一行。
 *
 * 和 Emp 实体的区别有两个：
 *   1. 没有 password。列表接口不需要密码，实体上虽然标了 WRITE_ONLY
 *      也能挡住，但「压根就没有这个字段」是最彻底的挡法
 *   2. 多了 deptName。emp 表里只存了 dept_id，部门名字是靠关联查询
 *      从 dept 表带出来的——所以这个字段来自 SQL 的 JOIN，不是数据库的列
 *
 * 为什么不直接在 Emp 实体上加个 deptName 字段？因为那是张冠李戴：
 * Emp 是 emp 表的镜像，deptName 不在那张表里。多表查询的结果应该由 VO 承载。
 */
@Data
public class EmpVO {

    private Long id;

    private String username;

    private String name;

    private Integer gender;

    /** 头像图片的访问 URL */
    private String image;

    /** 1班主任 2讲师 3学工主管 4教研主管 5咨询师 */
    private Integer job;

    private LocalDate entryDate;

    private Long deptId;

    /** 关联 dept 表查出来的部门名称，可能为 null（该员工不属于任何部门） */
    private String deptName;

    /** 1在职 2离职 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
