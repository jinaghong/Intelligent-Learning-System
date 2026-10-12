package com.hjh.tlias.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程成绩统计的一行。
 * 无成绩的课程 examCount=0，其余字段为 null（行保留，不许丢）。
 */
@Data
public class CourseScoreVO {

    private String courseName;

    private Integer examCount;

    private BigDecimal avgScore;

    private BigDecimal maxScore;

    private BigDecimal minScore;

    /** 及格率百分比，保留 1 位小数：及格人次(分数 >= 60) / 考试人次 × 100 */
    private BigDecimal passRate;

}
