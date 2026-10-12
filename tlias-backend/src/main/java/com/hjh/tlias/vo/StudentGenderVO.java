package com.hjh.tlias.vo;

import lombok.Data;

/**
 * 性别分布的一行。
 */
@Data
public class StudentGenderVO {

    /** 性别编码：1男 2女 */
    private Integer gender;

    private String genderName;

    private Integer count;

}
