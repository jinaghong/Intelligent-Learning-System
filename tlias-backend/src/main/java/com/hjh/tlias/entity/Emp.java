package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工。
 *
 * 数据库列名是下划线风格（entry_date），Java 属性是驼峰风格（entryDate）。
 * 这个对应关系已经在 application-dev.yml 里开了 map-underscore-to-camel-case，
 * 所以不用给每个字段写 @TableField("entry_date")。
 *
 * 三类日期时间类型别搞混：
 *   LocalDate     —— 只有年月日，对应 DATE（入职日期，不关心几点）
 *   LocalTime     —— 只有时分秒，对应 TIME
 *   LocalDateTime —— 年月日时分秒，对应 DATETIME
 */
@Data
@TableName("emp")
public class Emp {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /**
     * BCrypt 密文。
     *
     * WRITE_ONLY 的含义是「只允许写进来、永不允许读出去」：
     *   - 接收前端提交时可以被反序列化（P4 新增员工要传密码）
     *   - 生成响应时**绝不会**被序列化进 JSON
     * 用注解从源头上堵死，比在每个接口里记得 setPassword(null) 可靠——
     * 后者只要漏一处，密码哈希就泄露了。
     * （注意只挡 JSON 序列化；日志里打 Emp 对象仍会打印出来，别乱打）
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String name;

    /** 1男 2女 */
    private Integer gender;

    private String image;

    /** 1班主任 2讲师 3学工主管 4教研主管 5咨询师 */
    private Integer job;

    private LocalDate entryDate;

    private Long deptId;

    /** 1在职 2离职 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
