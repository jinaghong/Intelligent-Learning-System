package com.hjh.tlias.vo;

import lombok.Data;

/**
 * 班级违纪统计的一行。无违纪的班级以 0 出现（不许丢行）。
 */
@Data
public class ClazzViolationVO {

    private String clazzName;

    private Integer violationCount;

    private Integer totalPoints;

}
