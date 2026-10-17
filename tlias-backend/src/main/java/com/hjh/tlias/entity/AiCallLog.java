package com.hjh.tlias.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用日志（对应 ai_call_log 表，P0.5 已建好）。
 *
 * 和 oper_log 的分工（两本账）：
 *   oper_log    —— 「人做了什么操作」：谁调了哪个接口、成功失败
 *   ai_call_log —— 「AI 调用的细节」：发了什么 prompt、回了什么、花了多少 token、
 *                  耗时多少、模型是谁。token 是钱，这张表就是成本审计的账本。
 *
 * 刻意不建外键（同 oper_log）：发起人被删除后，审计记录也要留存。
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型：ADVICE 学习建议 / QUESTION 智能出题（见 AiServiceImpl 里的常量） */
    private String bizType;

    /** 实际服务的模型名；降级时记 FALLBACK */
    private String model;

    /** 发起人（登录员工）id；不建外键 */
    private Long empId;

    /** 发送给模型的完整提示词 */
    private String prompt;

    /** 模型返回的完整内容（降级时记兜底文本） */
    private String response;

    private Integer promptTokens;

    private Integer completionTokens;

    /** 耗时，单位毫秒 */
    private Long costTime;

    /** 1 成功 0 失败或已降级 */
    private Integer status;

    /** 失败或降级原因 */
    private String errorMsg;

    /** 调用时间。不赋值，交给数据库的 DEFAULT CURRENT_TIMESTAMP 填 */
    private LocalDateTime createTime;

}
