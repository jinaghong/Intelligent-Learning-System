package com.hjh.tlias.vo;

import lombok.Data;

/**
 * 部门人数统计的一行。0 人的部门以 0 出现（不许丢行）。
 */
@Data
public class DeptEmpCountVO {

    private String deptName;

    private Integer total;

    private Integer activeCount;

    private Integer inactiveCount;

}
