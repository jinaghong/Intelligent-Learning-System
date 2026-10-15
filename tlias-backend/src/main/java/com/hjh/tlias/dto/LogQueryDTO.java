package com.hjh.tlias.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 操作日志的查询条件。只有分页参数，没有筛选条件（YAGNI——
 * 等 P9 前端日志页有真实需求时再加操作人 / 时间段筛选）。
 *
 * page / pageSize 的默认值与 @Min(1) 下限校验，和 EmpQueryDTO 是同一套套路。
 */
@Data
public class LogQueryDTO {

    @Min(value = 1, message = "页码必须从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为 1")
    private Integer pageSize = 10;

}
