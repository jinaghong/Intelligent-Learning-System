package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 学员成绩。P5a 阶段只用到它的一个信息量：
 * 「这门课有没有成绩」——课程删除守卫要先数一数。
 * 完整的成绩管理不在 P5 范围里（它是 P6 数据统计的原料）。
 */
@Data
@TableName("student_score")
public class StudentScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long courseId;

    private LocalDate examDate;

    /** 分数，0.00 - 100.00，同样是精确小数 */
    private BigDecimal score;

}
