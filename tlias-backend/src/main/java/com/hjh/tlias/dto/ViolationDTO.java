package com.hjh.tlias.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 违纪处理的请求体。
 *
 * point 的 @Min(1) 不是随手加的：0 或负数会让「累计扣分」这个语义失效
 * （还能反向刷分），必须拦在门外。
 */
@Data
public class ViolationDTO {

    @NotNull(message = "学员 id 不能为空")
    private Long studentId;

    @NotNull(message = "扣分不能为空")
    @Min(value = 1, message = "扣分必须是正整数")
    private Integer point;

    @NotBlank(message = "违纪事由不能为空")
    private String description;

}
