package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门。对应数据库的 dept 表，一共 4 列。
 *
 * 列名 name / create_time / update_time 到属性的映射靠 application-dev.yml 里
 * 打开的 map-underscore-to-camel-case 自动完成，不需要逐个写 @TableField。
 *
 * 注意 createTime / updateTime 这两个字段：数据库那一侧已经写了
 * DEFAULT CURRENT_TIMESTAMP 和 ON UPDATE CURRENT_TIMESTAMP，
 * 也就是「插入时自动填、每次 UPDATE 自动刷新」。所以 Java 侧只管读、不用管写，
 * insert 和 update 时都不给它们赋值，留给数据库去填。
 */
@Data
@TableName("dept")
public class Dept {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
