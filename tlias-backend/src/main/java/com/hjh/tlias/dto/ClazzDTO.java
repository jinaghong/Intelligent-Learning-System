package com.hjh.tlias.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 班级的新增 / 修改请求体。
 */
@Data
public class ClazzDTO {

    /** 新增时不需要传，修改时必传 */
    private Long id;

    @NotBlank(message = "班级名称不能为空")
    private String name;

    private String room;

    @NotNull(message = "开班日期不能为空")
    private LocalDate beginDate;

    @NotNull(message = "结班日期不能为空")
    private LocalDate endDate;

    /** 可以不选班主任（master_id 允许 null），选了就必须真实存在（Service 校验） */
    private Long masterId;

    /**
     * 学科编码。@Min/@Max 把「枚举一致性」钉死在参数层：
     * 库里 1Java 2前端 3大数据 4Python 5测试，超出这个范围的值根本进不来，
     * 前端下拉框、后端编码、数据库注释三处的口径不会漂移。
     */
    @NotNull(message = "学科不能为空")
    @Min(value = 1, message = "学科编码必须是 1~5")
    @Max(value = 5, message = "学科编码必须是 1~5")
    private Integer subject;

    /** 1在读 2结课；不传默认在读（在 Service 里补） */
    private Integer status;

}
