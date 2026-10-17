package com.hjh.tlias.vo;

import lombok.Data;

import java.util.List;

/**
 * 一道题。
 *
 * 这个形状和 Prompt 里要求模型输出的 JSON 字段一一对应
 * （stem / options / answer / analysis）——中间隔着一次解析，
 * PromptTemplates 里的格式约定改了，这里也要跟着改。
 */
@Data
public class QuestionVO {

    /** 题干 */
    private String stem;

    /** 选项（约定 4 个） */
    private List<String> options;

    /** 正确选项，如 "A" */
    private String answer;

    /** 解析 */
    private String analysis;

}
