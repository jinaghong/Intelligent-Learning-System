package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志。对应 oper_log 表，由 LogAspect 在运行时写入。
 *
 * 和业务实体的两个不同：
 *
 * 1. 没有 createTime / updateTime。日志是「写完就不再变」的事实记录，
 *    只有 operateTime（操作发生时间），由切面在写入前现填——
 *    它太重要了，不能交给数据库默认值悄悄决定。
 *
 * 2. methodParams / returnValue / errorMsg 在数据库里是 TEXT 列，
 *    Java 侧就是 String（内容是 JSON 或异常信息）。TEXT 是超长字符串，
 *    不是 Java 的某种对象，别被名字误导。
 */
@Data
@TableName("oper_log")
public class OperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 id。允许为 null：登录接口被调用时还没有身份 */
    private Long empId;

    private LocalDateTime operateTime;

    private String className;

    private String methodName;

    /** 方法入参，JSON 数组字符串；密码等敏感字段已脱敏为 ****** */
    private String methodParams;

    /** 方法返回值，JSON 字符串；方法抛异常时此列为 null */
    private String returnValue;

    /** 耗时，毫秒 */
    private Long costTime;

    private String ip;

    /** 1 成功，0 失败（方法抛了异常） */
    private Integer status;

    /** 失败时的异常信息；成功时为 null */
    private String errorMsg;

}
