package com.hjh.tlias.vo;

import lombok.Data;

/**
 * 违纪 Top 榜的一行。
 * 数据直接来自 student 的汇总字段（P5 的冗余设计在此兑现：不用再聚合明细表）。
 */
@Data
public class ViolationTopVO {

    private String studentName;

    private String clazzName;

    private Integer violationCount;

    private Integer violationScore;

}
