package com.hjh.tlias.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * Excel 导出的一行。
 *
 * 为什么不直接拿 EmpVO 去导出：
 *   1. 库里存的是编码（gender=1、job=3、status=1），导出来是给人看的表格，
 *      得是「男 / 学工主管 / 在职」。EmpVO 承载的是接口数据，不该为了导出去改它
 *   2. 列的顺序 = 字段声明顺序，用独立的类才不用担心哪天给 EmpVO 加个字段，
 *      导出的表格里就凭空多出一列
 *   3. 万一以后有人往 EmpVO 里加了 password 相关的字段，导出文件也不会跟着泄露
 *
 * @ExcelProperty("表头文字") 就是 EasyExcel 的全部配置量：
 * 标了它的字段会被写进表格，值就是表头那一格。
 */
@Data
public class EmpExportVO {

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("姓名")
    private String name;

    /** 不是 1/2，是「男」「女」 */
    @ExcelProperty("性别")
    private String gender;

    /** 不是 1~5，是「班主任」「讲师」这种 */
    @ExcelProperty("职位")
    private String job;

    /** 格式化成 yyyy-MM-dd 的字符串，避免表格里出现一串时间数字 */
    @ExcelProperty("入职日期")
    private String entryDate;

    /** 该员工不属于任何部门时是空字符串，不是 null */
    @ExcelProperty("部门")
    private String deptName;

    /** 「在职」或「离职」 */
    @ExcelProperty("状态")
    private String status;

}
