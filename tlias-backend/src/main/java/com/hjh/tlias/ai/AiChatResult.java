package com.hjh.tlias.ai;

/**
 * 一次模型调用的结果。
 *
 * 为什么用 record 而不是普通类：它是「不可变的数据载体」——四个字段一次成型、
 * 只读、没有行为。record 一行顶掉 getter/构造/setter/equals 全套样板。
 * 数据离开这个对象时（落 ai_call_log、回给前端 VO）就没人再改它了。
 *
 * @param content          模型回答的正文
 * @param model            实际服务的模型名（降级时业务层改记 FALLBACK，不落在这里）
 * @param promptTokens     输入消耗的 token 数
 * @param completionTokens 输出消耗的 token 数
 */
public record AiChatResult(String content, String model, int promptTokens, int completionTokens) {
}
