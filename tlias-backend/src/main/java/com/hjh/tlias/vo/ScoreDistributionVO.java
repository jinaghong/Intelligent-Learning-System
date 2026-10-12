package com.hjh.tlias.vo;

import lombok.Data;

/**
 * 分数段分布的一行。
 * range 固定 5 档：0-59 / 60-69 / 70-79 / 80-89 / 90-100，空桶补 0。
 */
@Data
public class ScoreDistributionVO {

    private String range;

    private Integer count;

}
