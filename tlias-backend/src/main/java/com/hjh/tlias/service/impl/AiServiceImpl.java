package com.hjh.tlias.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjh.tlias.ai.AiChatResult;
import com.hjh.tlias.ai.AiChatService;
import com.hjh.tlias.ai.PromptTemplates;
import com.hjh.tlias.common.exception.BusinessException;
import com.hjh.tlias.context.BaseContext;
import com.hjh.tlias.dto.QuestionQueryDTO;
import com.hjh.tlias.entity.AiCallLog;
import com.hjh.tlias.entity.Course;
import com.hjh.tlias.entity.StudentScore;
import com.hjh.tlias.mapper.AiCallLogMapper;
import com.hjh.tlias.mapper.CourseMapper;
import com.hjh.tlias.mapper.StudentScoreMapper;
import com.hjh.tlias.service.AiService;
import com.hjh.tlias.service.StudentService;
import com.hjh.tlias.vo.AdviceVO;
import com.hjh.tlias.vo.QuestionVO;
import com.hjh.tlias.vo.StudentDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 增强核心编排。一次请求的固定五步：
 *
 *   ① 查数据（学员 + 成绩）→ ② 拼 Prompt → ③ 调模型 → ④ 写 ai_call_log → ⑤ 返回
 *
 * 三个刻意的设计：
 *
 * 1. 方法**不加 @Transactional**：出题降级那条路径是「先写 status=0 的日志、
 *    再抛业务异常」——如果整个方法在事务里，抛异常会把日志行一起回滚掉。
 *    审计记录必须独立提交（教训来自 P7：审计不能和业务同生共死）。
 *
 * 2. 「学员不存在」是业务参数错误，发生在调 AI **之前**——不写 ai_call_log。
 *    那张表记的是「AI 调用」的账，不是「参数校验」的账。
 *
 * 3. 日志写入包 try/catch：审计丢失可以事后补，业务挂了是事故（P7 同款教训）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    /** biz_type 取值：智能学习建议 */
    public static final String BIZ_ADVICE = "ADVICE";

    /** biz_type 取值：智能出题 */
    public static final String BIZ_QUESTION = "QUESTION";

    /** 降级时记在 model 字段的标记（ai_call_log 里一眼看出没真调模型） */
    public static final String MODEL_FALLBACK = "FALLBACK";

    private final StudentService studentService;
    private final StudentScoreMapper studentScoreMapper;
    private final CourseMapper courseMapper;
    private final AiChatService aiChatService;
    private final AiCallLogMapper aiCallLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AdviceVO advice(Long studentId) {
        // ① 查数据：学员不存在时 getDetailById 会抛「学员不存在」（P5 的守卫）
        StudentDetailVO student = studentService.getDetailById(studentId);
        List<StudentScore> scores = studentScoreMapper.selectList(
                new LambdaQueryWrapper<StudentScore>()
                        .eq(StudentScore::getStudentId, studentId)
                        .orderByAsc(StudentScore::getId));

        // 成绩表里只有 courseId，课程名要另查一次（不做 JOIN：这里两边都是小表、简单优先）
        Map<Long, String> courseNames = new HashMap<>();
        if (!scores.isEmpty()) {
            List<Long> courseIds = scores.stream().map(StudentScore::getCourseId).distinct().toList();
            for (Course course : courseMapper.selectBatchIds(courseIds)) {
                courseNames.put(course.getId(), course.getName());
            }
        }
        List<PromptTemplates.ScoreEntry> entries = scores.stream()
                .map(s -> new PromptTemplates.ScoreEntry(
                        courseNames.getOrDefault(s.getCourseId(), "未知课程"), s.getScore()))
                .toList();

        // ② 拼 Prompt → ③ 调模型（计时包住调用本身，不含查库时间）
        String prompt = PromptTemplates.advicePrompt(student, entries);
        long start = System.currentTimeMillis();

        AdviceVO vo = new AdviceVO();
        vo.setStudentId(studentId);
        vo.setStudentName(student.getName());
        vo.setCreateTime(LocalDateTime.now());

        try {
            AiChatResult result = aiChatService.chat(prompt);
            long costTime = System.currentTimeMillis() - start;

            // ④ 落审计账
            recordCall(BIZ_ADVICE, result.model(), prompt, result.content(),
                    result.promptTokens(), result.completionTokens(), costTime, 1, null);

            vo.setAdvice(result.content());
            vo.setModel(result.model());
        } catch (Exception e) {
            // 降级：建议有「规则版兜底」——用刚查出的库数据拼一段摘要，仍然有信息量。
            // 失败也要记账（status=0 + 原因），审计不因为失败而断档
            long costTime = System.currentTimeMillis() - start;
            String fallback = ruleBasedAdvice(student, entries);
            recordCall(BIZ_ADVICE, MODEL_FALLBACK, prompt, fallback, 0, 0, costTime, 0, e.getMessage());
            vo.setAdvice(fallback);
            vo.setModel(MODEL_FALLBACK);
        }

        // ⑤ 返回（成功走 model，失败走 FALLBACK——model 字段让前端能看出区别）
        return vo;
    }

    @Override
    public List<QuestionVO> questions(QuestionQueryDTO query) {
        // ① 查数据：课程不存在同样是「调 AI 之前」的业务错误，不写日志
        Course course = courseMapper.selectById(query.getCourseId());
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        int count = query.getCount() == null ? 5 : query.getCount();
        int difficulty = query.getDifficulty() == null ? 2 : query.getDifficulty();

        // ② 拼 Prompt → ③ 调模型
        String prompt = PromptTemplates.questionPrompt(course.getName(), count, difficulty);
        long start = System.currentTimeMillis();
        AiChatResult result = null;
        try {
            result = aiChatService.chat(prompt);
            List<QuestionVO> questions = parseQuestions(result.content());
            long costTime = System.currentTimeMillis() - start;

            // ④ 落审计账
            recordCall(BIZ_QUESTION, result.model(), prompt, result.content(),
                    result.promptTokens(), result.completionTokens(), costTime, 1, null);
            return questions;
        } catch (Exception e) {
            // 降级：出题没有可替代品（规则编不出题）——记完失败账，抛友好业务异常，
            // 全局异常处理器会把它转成 code 0（HTTP 200），前端不会收到 500。
            // response 保留模型原始输出（如果是解析失败，这里能看出它到底回了什么）
            long costTime = System.currentTimeMillis() - start;
            recordCall(BIZ_QUESTION, MODEL_FALLBACK, prompt,
                    result == null ? null : result.content(), 0, 0, costTime, 0, e.getMessage());
            throw new BusinessException("智能出题服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 规则版兜底建议：不用大模型，直接用库数据拼一段摘要。
     * 质量不如 AI，但「有信息量」和「什么都没有」是本质区别——
     * 降级不等于免责，能给的先给。
     */
    private String ruleBasedAdvice(StudentDetailVO student, List<PromptTemplates.ScoreEntry> entries) {
        StringBuilder sb = new StringBuilder("【规则版建议】AI 服务暂时不可用，以下为系统按规则自动生成的摘要：\n");
        sb.append("学员 ").append(student.getName())
                .append(" 违纪 ").append(student.getViolationCount())
                .append(" 次、累计扣分 ").append(student.getViolationScore()).append(" 分。\n");
        if (entries.isEmpty()) {
            sb.append("暂无考试成绩可分析。\n");
        } else {
            PromptTemplates.ScoreEntry lowest = entries.stream()
                    .min(Comparator.comparing(PromptTemplates.ScoreEntry::score))
                    .orElse(entries.get(0));
            sb.append("建议优先补强得分最低的课程「").append(lowest.courseName())
                    .append("」（").append(lowest.score()).append(" 分）。\n");
        }
        sb.append("请保持全勤、减少违纪，必要时联系任课老师安排针对性辅导。");
        return sb.toString();
    }

    /**
     * 把模型输出的文本解析成题目列表。
     *
     * 三级防线：先剥 Markdown 围栏 → 要求是 JSON 数组 → 逐字段取值。
     * 解析失败抛业务异常，由 questions() 的 catch 统一转成降级
     * （记 status=0 的账 + 给前端友好提示）——解析失败也是失败的一种。
     */
    private List<QuestionVO> parseQuestions(String content) {
        String json = PromptTemplates.stripFences(content);
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException("AI 回答解析失败：" + e.getMessage());
        }
        if (root == null || !root.isArray()) {
            throw new BusinessException("AI 回答解析失败：输出不是 JSON 数组");
        }
        List<QuestionVO> list = new ArrayList<>();
        for (JsonNode node : root) {
            QuestionVO vo = new QuestionVO();
            vo.setStem(node.path("stem").asText());
            List<String> options = new ArrayList<>();
            node.path("options").forEach(option -> options.add(option.asText()));
            vo.setOptions(options);
            vo.setAnswer(node.path("answer").asText());
            vo.setAnalysis(node.path("analysis").asText());
            list.add(vo);
        }
        return list;
    }

    /**
     * 写一行 ai_call_log。成功失败都走这里，只是 status/errorMsg 不同。
     */
    private void recordCall(String bizType, String model, String prompt, String response,
                            int promptTokens, int completionTokens, long costTime,
                            int status, String errorMsg) {
        AiCallLog row = new AiCallLog();
        row.setBizType(bizType);
        row.setModel(model);
        row.setEmpId(BaseContext.getCurrentId());
        row.setPrompt(prompt);
        row.setResponse(response);
        row.setPromptTokens(promptTokens);
        row.setCompletionTokens(completionTokens);
        row.setCostTime(costTime);
        row.setStatus(status);
        row.setErrorMsg(errorMsg);
        try {
            aiCallLogMapper.insert(row);
        } catch (Exception e) {
            log.error("ai_call_log 写入失败（不影响业务返回）：bizType={}, 原因={}", bizType, e.getMessage());
        }
    }

}
