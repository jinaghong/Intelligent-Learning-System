package com.hjh.tlias.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习建议的返回体。
 *
 * model 字段是刻意暴露给前端的：
 *   - "mock"        → 开发环境假数据
 *   - 真实模型名     → 真调用
 *   - "FALLBACK"    → AI 失败，返回的是规则版兜底（P9 前端可以据此加标注）
 */
@Data
public class AdviceVO {

    private Long studentId;

    private String studentName;

    private String advice;

    private String model;

    private LocalDateTime createTime;

}
