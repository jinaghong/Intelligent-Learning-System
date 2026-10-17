package com.hjh.tlias.ai;

import com.hjh.tlias.vo.StudentDetailVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 提示词模板集中营（P8 阶段「骨架我搭，Prompt 你调优」的那个文件）。
 *
 * 为什么集中：提示词散在各处时，调优要满仓库找；集中在这一个文件里，
 * 改措辞只动一处。当前先给一版能跑的初稿，后续按实际输出效果迭代（真 key 到位后）。
 *
 * 拼 Prompt 的本质：把数据库里查出来的事实（学员姓名、成绩、违纪……）
 * 翻译成一段大模型看得懂的自然语言。模型不会查库，它只认识这段文字——
 * 所以「发什么数据给模型」就是「模型能基于什么给建议」。
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /** 一条成绩记录：课程名 + 分数（由 AiServiceImpl 从库数据拼出来） */
    public record ScoreEntry(String courseName, BigDecimal score) {
    }

    /**
     * 学习建议的提示词。
     *
     * 要求输出 150~300 字的分点建议——不是技术限制，是产品约束：
     * 太长前端放不下、老师不爱看；不设约束时模型倾向于写小作文。
     */
    public static String advicePrompt(StudentDetailVO student, List<ScoreEntry> scores) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名培训学校的教务老师。请根据以下学员的数据，");
        sb.append("给学生所在班级的班主任写一段可执行的学习建议。\n");
        sb.append("学员姓名：").append(student.getName()).append("\n");
        sb.append("学历：").append(student.getDegree()).append("\n");
        sb.append("班级：").append(student.getClazzName()).append("\n");
        sb.append("违纪次数：").append(student.getViolationCount())
                .append("，累计违纪扣分：").append(student.getViolationScore()).append("\n");
        sb.append("各课程成绩：\n");
        if (scores.isEmpty()) {
            sb.append("  （暂无考试成绩）\n");
        } else {
            for (ScoreEntry entry : scores) {
                sb.append("  - ").append(entry.courseName()).append("：").append(entry.score()).append(" 分\n");
            }
        }
        sb.append("要求：中文，150~300 字，先指出薄弱点，再给具体可执行的改进建议，分点输出。");
        return sb.toString();
    }

    /**
     * 智能出题的提示词。
     *
     * 和「学习建议」的关键区别：这里要求**严格输出 JSON**——因为返回值要
     * 被程序解析成结构化的 QuestionVO 列表，而不是给人类读的一段话。
     * 「只输出 JSON、不要代码块」这句约束直接影响解析成功率；
     * 模型偶尔还是会包 ```json 围栏，所以解析前有 stripFences 兜底。
     */
    public static String questionPrompt(String courseName, int count, int difficulty) {
        String difficultyText = switch (difficulty) {
            case 1 -> "简单";
            case 3 -> "困难";
            default -> "中等";
        };
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名培训学校的教研老师，请为学员出练习题。\n");
        sb.append("课程名称：").append(courseName).append("\n");
        sb.append("题目数量：").append(count).append("\n");
        sb.append("难度：").append(difficultyText).append("\n");
        sb.append("要求：只输出一个 JSON 数组，不要输出任何其他文字、不要 Markdown 代码块。\n");
        sb.append("每个元素包含四个字段：stem（题干）、options（4 个选项的字符串数组）、");
        sb.append("answer（正确选项字母，如 \"A\"）、analysis（答案解析）。");
        return sb.toString();
    }

    /**
     * 剥掉模型输出外层可能包裹的 Markdown 代码块围栏。
     *
     * 模型被要求「只输出 JSON」，但实测中它时常还是会回：
     *   ```json
     *   [ ... ]
     *   ```
     * 直接丢给 Jackson 解析必炸。这里做最小清洗：trim 后如果以 ``` 开头、
     * 以 ``` 结尾，就把围栏那两行剥掉，只留中间的内容。
     */
    public static String stripFences(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline >= 0) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        return text;
    }

}
