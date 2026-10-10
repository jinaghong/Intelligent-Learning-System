package com.hjh.tlias.vo;

import com.hjh.tlias.entity.EmpExperience;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 员工回显（点「编辑」时用）。
 *
 * 它比列表用的 EmpVO 只多一个字段——工作经历列表。其余字段（用户名、姓名、
 * 性别、职位、入职日期、部门名……）完全一样，所以用**继承**而不是把
 * EmpVO 的字段抄一遍：抄一遍的话，以后 EmpVO 加字段，这里忘了跟，两边就不一致了。
 *
 * @EqualsAndHashCode(callSuper = true) 是 Lombok 的要求：父类字段也要参与
 * equals/hashCode 计算。不写只会有一条编译警告，但语义上是错的。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmpDetailVO extends EmpVO {

    /** 该员工的工作经历，可能为空列表 */
    private List<EmpExperience> exprList;

}
