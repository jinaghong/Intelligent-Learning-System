package com.hjh.tlias.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一条违纪记录（学员详情里的明细行）。
 */
@Data
public class ViolationVO {

    private Long id;

    private Long studentId;

    private Integer point;

    private String description;

    private LocalDateTime createTime;

}
