package com.hjh.tlias.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 学员列表的查询条件。模具和 EmpQueryDTO / ClazzQueryDTO 完全一样：
 * 条件可空（空 = 不参与筛选），page / pageSize 带默认值和下限校验。
 */
@Data
public class StudentQueryDTO {

    /** 姓名，模糊匹配 */
    private String name;

    /** 学历，精确匹配：1初中 ~ 6博士 */
    private Integer degree;

    /** 所属班级，精确匹配 */
    private Long clazzId;

    @Min(value = 1, message = "页码必须从 1 开始")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为 1")
    private Integer pageSize = 10;

}
