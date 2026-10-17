package com.hjh.tlias.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 真实现：走 Spring AI 的 ChatClient 调 DeepSeek。
 *
 * DeepSeek 提供的是「OpenAI 兼容协议」接口——请求和响应的 JSON 形状与 OpenAI 一致，
 * 所以这里用 OpenAI 的 starter（配置里把 base-url 指到 api.deepseek.com）。
 *
 * 这个类在默认 profile 下不会被创建（@Profile("deepseek")），
 * 所以没 key、没网的环境启动不受影响。
 */
@Service
@Profile("deepseek")
public class DeepSeekAiChatServiceImpl implements AiChatService {

    private final ChatClient chatClient;

    /**
     * ChatClient.Builder 是 Spring AI 自动配置好的（读取 spring.ai.openai.* 配置）。
     * 构造时 build 一次，之后每次请求复用——ChatClient 设计为线程安全。
     */
    public DeepSeekAiChatServiceImpl(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public AiChatResult chat(String prompt) {
        ChatResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();

        String content = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();
        return new AiChatResult(
                content,
                response.getMetadata().getModel(),
                // Usage 的 token 数可能为 null（某些厂商不返回），null 一律按 0 记
                usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
    }

}
