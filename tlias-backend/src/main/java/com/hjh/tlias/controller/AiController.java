package com.hjh.tlias.controller;

import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.dto.QuestionQueryDTO;
import com.hjh.tlias.service.AiService;
import com.hjh.tlias.vo.AdviceVO;
import com.hjh.tlias.vo.QuestionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 增强（P8）。
 *
 * 两个接口都是 GET 只读、需登录——拦截器默认全拦，这里不开口子，
 * 和 /report 的待遇一致：AI 查询是花钱的操作，绝不能匿名裸奔。
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI 增强")
public class AiController {

    private final AiService aiService;

    @GetMapping("/advice/{studentId}")
    @Operation(summary = "生成学员学习建议")
    public Result<AdviceVO> advice(@PathVariable Long studentId) {
        return Result.success(aiService.advice(studentId));
    }

    @GetMapping("/questions")
    @Operation(summary = "生成课程练习题")
    public Result<List<QuestionVO>> questions(@Valid QuestionQueryDTO query) {
        return Result.success(aiService.questions(query));
    }

}
