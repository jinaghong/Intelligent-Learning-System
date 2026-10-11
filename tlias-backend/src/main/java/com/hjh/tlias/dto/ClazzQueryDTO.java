package com.hjh.tlias.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 班级列表的查询条件。GET 请求靠参数绑定逐个塞进来，字段名要和前端参数名一致。
 *
 * 和 EmpQueryDTO 是同一个模具：条件全部可空（空 = 不参与筛选），
 * page/pageSize 带默认值和 @Min 下限。
 */
@Data
public class ClazzQueryDTO {

    /** 班级名称，模糊匹配 */
    private String name;

    /** 开班日期区间的起点，含当天 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate begin;

    /** 开班日期区间的终点，含当天 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;

    @Min(value = 1, message = "页码必须从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为 1")
    private Integer pageSize = 10;

}
