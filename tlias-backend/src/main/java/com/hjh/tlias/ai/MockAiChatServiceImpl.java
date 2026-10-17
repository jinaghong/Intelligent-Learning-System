package com.hjh.tlias.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 假实现：不连网络，返回固定的模拟内容。
 *
 * 为什么默认用它（@Profile("!deepseek")）：
 *   1. 测试断言没法基于真模型——它每次回答都不一样；Mock 输出固定，好断言
 *   2. token 是要花钱的，测试跑几十遍不该产生费用
 *   3. 没网/没 key 的开发环境照样能跑通全链路
 *   真调用属于 deepseek profile（application-deepseek.yml），测试永不开它。
 *
 * 类比：电影里的假电话道具——演员照常拨号、照常说话，电话不通到真人。
 */
@Service
@Profile("!deepseek")
public class MockAiChatServiceImpl implements AiChatService {

    /** 输出里固定带的标记，测试和人工验收靠它识别「这条来自 Mock」 */
    public static final String MARKER = "[MOCK]";

    /** 固定的模拟模型名，将来 ai_call_log 里看到它就知道没真调 */
    public static final String MODEL = "mock";

    /** 模拟「调用失败」（降级测试用，@SpringBootTest properties 打开） */
    @Value("${ai.mock.force-fail:false}")
    private boolean forceFail;

    /** 模拟「调用成功但输出没法解析成 JSON」（降级测试用） */
    @Value("${ai.mock.bad-json:false}")
    private boolean badJson;

    @Override
    public AiChatResult chat(String prompt) {
        // 故障开关优先：模拟真实调用的各种失败，让降级路径可复现、可回归。
        // 靠运气触发的东西没法测试——所以开关只在测试里打开，生产永远 false。
        if (forceFail) {
            throw new IllegalStateException("模拟 AI 调用失败（测试开关 ai.mock.force-fail）");
        }

        // 出题场景：Prompt 里带「题目数量：N」标记（PromptTemplates.questionPrompt 的约定），
        // 按数量生成一份合法的 JSON 数组——让「解析 JSON」这条链路在 Mock 下也能真实走通
        if (prompt != null && prompt.contains("题目数量：")) {
            if (badJson) {
                return new AiChatResult("这不是合法 JSON 的内容 {{{", MODEL, 0, 0);
            }
            return new AiChatResult(mockQuestions(extractCount(prompt)), MODEL, 0, 0);
        }
        // 其余按「学习建议」场景处理
        String content = MARKER + " 这是一条模拟的学习建议（未调用真实大模型）："
                + "建议先补齐薄弱课程的基础知识，把错题按知识点归类、每天固定 30 分钟专项练习，"
                + "同时保持全勤、避免违纪扣分，必要时向任课老师申请一对一答疑。";
        return new AiChatResult(content, MODEL, 0, 0);
    }

    /** 从「题目数量：N」里抠出 N；抠不到就按 3 兜底 */
    private int extractCount(String prompt) {
        Matcher m = Pattern.compile("题目数量：(\\d+)").matcher(prompt);
        return m.find() ? Integer.parseInt(m.group(1)) : 3;
    }

    /** 生成 n 道题的 JSON 数组，形状与 PromptTemplates.questionPrompt 的字段约定一致 */
    private String mockQuestions(int n) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 1; i <= n; i++) {
            if (i > 1) {
                sb.append(",");
            }
            sb.append("{\"stem\":\"").append(MARKER).append(" 题目").append(i).append("：关于本课程的说法正确的是？\",")
                    .append("\"options\":[\"选项A（正确项）\",\"选项B（干扰项）\",\"选项C（干扰项）\",\"选项D（干扰项）\"],")
                    .append("\"answer\":\"A\",")
                    .append("\"analysis\":\"这是 Mock 生成的解析（第 ").append(i).append(" 题）。\"}");
        }
        return sb.append("]").toString();
    }

}
