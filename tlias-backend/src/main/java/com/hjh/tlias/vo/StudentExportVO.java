package com.hjh.tlias.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 班级学员名单导出的一行。
 *
 * 和 StudentVO 分开：导出的表格是给人看的，性别 / 学历 / 院校生源必须是中文；
 * 同时刻意不含身份证号、家庭住址，避免导出文件扩大隐私暴露面。
 */
@Data
public class StudentExportVO {

    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("学号")
    private String no;

    /** 不是 1/2，是「男」「女」 */
    @ExcelProperty("性别")
    private String gender;

    @ExcelProperty("手机号")
    private String phone;

    /** 不是 1~6，是「初中」到「博士」 */
    @ExcelProperty("学历")
    private String degree;

    @ExcelProperty("班级")
    private String clazzName;

    /** 「是」或「否」 */
    @ExcelProperty("院校生源")
    private String isCollege;

    @ExcelProperty("违纪次数")
    private Integer violationCount;

    @ExcelProperty("累计扣分")
    private Integer violationScore;

}
