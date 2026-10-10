package com.hjh.tlias.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * CacheService 的 Redis 实现（备而不用，先把位置占好）。
 *
 * 什么时候需要它：换成多实例部署后，验证码请求可能落到 A 机器、
 * 校验请求落到 B 机器，存各自内存的本地缓存就不够用了，需要集中式缓存。
 *
 * 当前状态：这台开发机没装 Redis，这条路径**没有被实测过**——
 * 只保证「编译通过、接口行为对齐 Caffeine 实现」。
 * 将来真要用时，先本地起一个 Redis（或 docker run redis），
 * 用「激活 profile 里带上 redis」的方式启动，跑一遍验证再上。
 *
 * 数据怎么存：值统一转成 JSON 字符串塞进 StringRedisTemplate。
 * 存的是字符串而不是 Java 序列化字节，好处是缓存里的内容用 redis-cli 肉眼可读、
 * 也不受类结构变更影响。
 */
@Slf4j
@Service
@Profile("redis")
@RequiredArgsConstructor
public class RedisCacheServiceImpl implements CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void put(String key, Object value, Duration ttl) {
        try {
            // set(key, value, ttl) 由 Redis 自己记过期，不需要我们算时刻
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("缓存值序列化成 JSON 失败: " + key, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            // JSON 是不带类型的文本，这里只能还原成「通用对象」
            // （字符串还原成 String、对象还原成 LinkedHashMap、数字还原成 Integer/Double）。
            // 对缓存验证码 / 字典这类简单数据够用；将来要存强类型对象时，
            // 得把类型信息也存进去（或换 Jackson 的带类型序列化），到时候再说
            return (T) objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            // 缓存内容坏了当作没命中，不能让一条脏数据把业务打挂
            log.warn("缓存内容不是合法 JSON，按未命中处理: {}", key);
            return null;
        }
    }

    @Override
    public void evict(String key) {
        redisTemplate.delete(key);
    }

}
