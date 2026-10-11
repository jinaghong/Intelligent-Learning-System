package com.hjh.tlias.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 学员的新增 / 修改请求体。
 *
 * violationCount / violationScore 不在这里——它们不是「填出来的」，
 * 是违纪处理累加出来的，让前端传反而是个数据安全漏洞。
 */
@Data
public class StudentDTO {

    /** 新增时不需要传，修改时必传 */
    private Long id;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "学号不能为空")
    private String no;

    @NotNull(message = "性别不能为空")
    @Min(value = 1, message = "性别编码必须是 1~2")
    @Max(value = 2, message = "性别编码必须是 1~2")
    private Integer gender;

    private String phone;

    /** 学历：1初中 2高中 3专科 4本科 5硕士 6博士。和学科一样把枚举边界钉在参数层 */
    @NotNull(message = "学历不能为空")
    @Min(value = 1, message = "学历编码必须是 1~6")
    @Max(value = 6, message = "学历编码必须是 1~6")
    private Integer degree;

    /** 可以不选班级（clazz_id 允许 null），选了就必须真实存在（Service 校验） */
    private Long clazzId;

    private String idCard;

    /** 是否院校合作生源：0否 1是；不传默认 0（在 Service 里补） */
    private Integer isCollege;

    private String address;

    private LocalDate graduationDate;

}
