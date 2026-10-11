package com.hjh.tlias.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 课程的新增 / 修改请求体。
 *
 * 和 DeptDTO 同一个套路：只暴露「这次请求该给我什么」，
 * createTime / updateTime 这种库自己维护的字段一概不给。
 */
@Data
public class CourseDTO {

    /** 新增时不需要传，修改时必传（靠它定位改哪一行） */
    private Long id;

    @NotBlank(message = "课程名称不能为空")
    private String name;

    @NotBlank(message = "课程编号不能为空")
    private String no;

    /**
     * @NotNull 管「没传」，@DecimalMin 管「传了但太小」。
     * 两个注解缺一不可：只写 @DecimalMin 的话，price 为 null 时它直接跳过校验，
     * null 会一路走到数据库（那一列是 NOT NULL），最后报「系统繁忙」。
     */
    @NotNull(message = "课程价格不能为空")
    @DecimalMin(value = "0", message = "课程价格不能为负数")
    private BigDecimal price;

    private String description;

}
