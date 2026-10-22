package com.hjh.tlias.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 员工列表的查询条件。
 *
 * GET 请求没有请求体，这里靠 Spring 的「参数绑定」把 URL 上的查询串
 * （?name=李&gender=1&page=2）逐个塞进这个对象的同名字段，
 * 所以字段名必须和前端传的参数名一致。
 *
 * 四个筛选条件全部可以为 null，为 null 就表示「这个条件不参与筛选」——
 * XML 里正是用 <if> 判断这一点，什么都不传就等价于查全表。
 */
@Data
public class EmpQueryDTO {

    /** 姓名，模糊匹配：传「丽」能搜到「黄丽」「孙丽」 */
    private String name;

    /** 性别：1男 2女。这个是精确匹配，不是模糊 */
    private Integer gender;

    /** 入职日期区间的起点，含当天 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate begin;

    /** 入职日期区间的终点，含当天 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;

    /** 所属部门，精确匹配；为空表示不按部门筛选 */
    private Long deptId;

    /**
     * 第几页，从 1 开始。
     *
     * 这里给了默认值，含义是「前端不传就用它」。注意必须是包装类型 Integer
     * 而不是 int——int 的默认值是 0，传进来的 null 没法表达「没传」，
     * 而且页码从 0 开始是错的。
     *
     * @Min(1) 负责下限：page=0 或负数会被拦成业务错误码（GET 接口的参数上
     * 必须配 @Valid，注解才会真正执行，见 EmpController）。
     */
    @Min(value = 1, message = "页码必须从 1 开始")
    private Integer page = 1;

    /**
     * 每页几条。
     *
     * 前端如果传一个很大的值想「一次拉全表」，会在分页插件那里被
     * MyBatisPlusConfig 里设的 setMaxLimit(500) 截住，最多只给 500 条。
     * 这是防止有人写 pageSize=999999999 把数据库拖垮。
     *
     * 下限由 @Min(1) 保证：0 和负数必须拦下——负数在分页插件里会走特殊分支
     * （跳过 COUNT、LIMIT 被 maxLimit 兜底），返回 total=0 却有数据的矛盾响应。
     */
    @Min(value = 1, message = "每页条数至少为 1")
    private Integer pageSize = 10;

}
