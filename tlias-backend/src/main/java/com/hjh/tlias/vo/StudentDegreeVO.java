package com.hjh.tlias.vo;

import lombok.Data;

/**
 * 学历分布的一行。
 * degreeName 在 Service 层由编码翻译（SQL 只出「编码 + 数量」，保持纯聚合）。
 */
@Data
public class StudentDegreeVO {

    /** 学历编码：1初中 2高中 3专科 4本科 5硕士 6博士 */
    private Integer degree;

    private String degreeName;

    private Integer count;

}
