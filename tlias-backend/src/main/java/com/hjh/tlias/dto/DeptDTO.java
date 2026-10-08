package com.hjh.tlias.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 部门的新增 / 修改请求体。
 *
 * 为什么不直接用 Dept 接请求体：Dept 是数据库表的镜像，前端能顺手提交
 * createTime、updateTime 把库里的时间覆盖掉。DTO 只暴露「这次请求该给我什么」，
 * 多一个字段都不给。LoginDTO 的注释里写过同一个道理。
 *
 * @NotBlank 和 @NotNull 的区别（很容易混）：
 *   @NotNull  —— 只要不是 null 就行，空字符串、纯空格都算通过
 *   @NotBlank —— 专门给字符串用：null、""、"   " 全都不通过
 *
 * 光写注解不生效，Controller 的参数上必须再加 @Valid 才会触发校验。
 */
@Data
public class DeptDTO {

    /** 新增时不需要传，修改时必传（靠它定位改哪一行） */
    private Long id;

    @NotBlank(message = "部门名称不能为空")
    private String name;

}
