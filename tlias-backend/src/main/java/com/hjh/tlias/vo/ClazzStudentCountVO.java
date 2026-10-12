package com.hjh.tlias.vo;

import lombok.Data;

/**
 * 班级人数统计的一行（柱状图数据）。
 */
@Data
public class ClazzStudentCountVO {

    private String clazzName;

    private Integer total;

    private Integer maleCount;

    private Integer femaleCount;

}
