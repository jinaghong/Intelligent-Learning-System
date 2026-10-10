package com.hjh.tlias.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 员工的新增 / 修改请求体。
 *
 * 和 Emp 实体的区别：没有 createTime / updateTime（那是数据库自己维护的），
 * 多了一个 exprList（工作经历列表，一对多的「多」方）。
 */
@Data
public class EmpDTO {

    /** 新增时不传，修改时必传 */
    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 登录密码。
     *
     * 故意**不加** @NotBlank —— 因为新增和修改对它的要求不一样：
     *   新增：必须传，不传就没法登录
     *   修改：可以不传，表示「这次不动密码」
     * 注解只能表达「永远必填」，这种场景相关的规则得写在 Service 里。
     */
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 1男 2女 */
    @NotNull(message = "性别不能为空")
    private Integer gender;

    /** 头像 URL。头像上传暂未实现，这里先留着 */
    private String image;

    /** 1班主任 2讲师 3学工主管 4教研主管 5咨询师 */
    @NotNull(message = "职位不能为空")
    private Integer job;

    @NotNull(message = "入职日期不能为空")
    private LocalDate entryDate;

    /** 可以没有部门（emp.dept_id 允许为 null），所以不加 @NotNull */
    private Long deptId;

    /** 1在职 2离职。不传时新增默认在职 */
    private Integer status;

    /**
     * 工作经历列表，可以是 null 或空列表（表示这个员工没有经历）。
     *
     * 字段上的 @Valid 作用是「级联校验」：不写它，只会校验 EmpDTO 自己的字段，
     * exprList 里每个 EmpExperienceDTO 上的 @NotBlank 全都不会执行——
     * 前端提交一条空公司名的经历也能过。
     */
    @Valid
    private List<EmpExperienceDTO> exprList;

}
