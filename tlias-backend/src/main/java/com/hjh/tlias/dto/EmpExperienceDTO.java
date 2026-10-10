package com.hjh.tlias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 一条工作经历（新增 / 修改员工时随 emp 一起提交）。
 *
 * 和 EmpExperience 实体的区别：这里**没有 empId**。
 * 前端提交一条经历时，它属于哪个员工是由「这次请求要新建/修改哪个员工」决定的，
 * 不该让前端自己填——填错了就能把经历挂到别人名下。
 */
@Data
public class EmpExperienceDTO {

    @NotNull(message = "工作经历的入职时间不能为空")
    private LocalDate beginDate;

    @NotNull(message = "工作经历的离职时间不能为空")
    private LocalDate endDate;

    @NotBlank(message = "工作经历的公司不能为空")
    private String company;

    @NotBlank(message = "工作经历的职位不能为空")
    private String job;

}
