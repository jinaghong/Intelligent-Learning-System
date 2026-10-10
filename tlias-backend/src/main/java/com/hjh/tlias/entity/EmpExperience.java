package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 员工工作经历。对应 emp_experience 表——「一对多」里的「多」方。
 *
 * 一个员工对应 0~N 条经历，靠 emp_id 这列关联回 emp 表。
 * 表上没有存「员工姓名」这类冗余信息，因为那是能 JOIN 出来的；
 * 只有当关联查询真的成为性能瓶颈时才考虑冗余，现在不需要。
 */
@Data
@TableName("emp_experience")
public class EmpExperience {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属员工 id。
     *
     * 注意这个值**不该由前端提交**：新增员工时前端还不知道员工的自增 id，
     * 是后端 insert 完员工、拿到回填的 id 之后，再逐条填进去的。
     */
    private Long empId;

    private LocalDate beginDate;

    private LocalDate endDate;

    private String company;

    private String job;

}
