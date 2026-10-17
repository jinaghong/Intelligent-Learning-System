package com.hjh.tlias.service;

import com.hjh.tlias.dto.QuestionQueryDTO;
import com.hjh.tlias.vo.AdviceVO;
import com.hjh.tlias.vo.QuestionVO;

import java.util.List;

/**
 * AI 增强业务入口（P8）。
 *
 * 职责边界：AiChatService 只管「问模型」，这里管「业务」——
 * 查数据库取素材、拼 Prompt、解析模型输出、落 ai_call_log、失败时降级。
 */
public interface AiService {

    /**
     * 生成某学员的学习建议。
     * 学员不存在时抛「学员不存在」；AI 调用失败时走规则版兜底（不抛异常）。
     */
    AdviceVO advice(Long studentId);

    /**
     * 为某课程生成练习题。
     * 课程不存在时抛「课程不存在」；AI 调用失败或输出解析失败时抛友好业务异常。
     */
    List<QuestionVO> questions(QuestionQueryDTO query);

}
