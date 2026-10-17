package com.hjh.tlias.ai;

/**
 * 大模型调用统一入口（「三层可替换抽象」的第三条腿）。
 *
 * 前两条：cache（Caffeine / Redis，@Profile 切换）、
 *         storage（Local / OSS，@Profile 切换）。
 * 这条：mock（默认）/ deepseek（真调用）——同一个套路：
 *   - 业务层只认识 chat(prompt) 一个方法，不知道背后是谁在回答
 *   - 两个实现靠 @Profile 切换，测试永远走 Mock（确定性、零成本、离线可跑）
 *
 * 为什么把「调用」和「业务」分开：AiService 管「查数据、拼 Prompt、记日志、降级」，
 * AiChatService 只管「给我一句话，我帮你问模型」。将来换模型/换厂商（千问、GLM……）
 * 只动实现类，业务代码一行不改。
 */
public interface AiChatService {

    /**
     * 发一段提示词，等模型回答。
     *
     * 调用失败（网络、超时、鉴权）时抛异常——降级是业务层（AiService）的决策，
     * 这里不做「吞异常返回空串」这种小动作：假装成功比失败更危险。
     *
     * @param prompt 完整提示词（由 PromptTemplates 拼好）
     * @return 回答正文 + 模型名 + token 用量
     */
    AiChatResult chat(String prompt);

}
