package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 班级。
 *
 * 两条外键都值得留意（看 schema.sql）：
 *   master_id → emp，ON DELETE SET NULL —— 删老师时班级不消失，只是暂时没班主任
 *   被 student.clazz_id 引用，RESTRICT —— 班里有学员时不许删
 */
@Data
@TableName("clazz")
public class Clazz {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 上课教室 */
    private String room;

    /** 开班 / 结班日期。DATE 类型对应 LocalDate，不含时分秒 */
    private LocalDate beginDate;

    private LocalDate endDate;

    /** 班主任，外键指向 emp.id；可以为 null（暂时没有班主任） */
    private Long masterId;

    /** 学科：1Java 2前端 3大数据 4Python 5测试 */
    private Integer subject;

    /** 状态：1在读 2结课 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
