package com.hjh.tlias.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * CacheService 的本地内存实现，基于 Caffeine。
 *
 * Caffeine 是 Java 里性能最好的本地缓存库（Spring Boot 默认的缓存实现就是它）。
 * 「本地」的含义：数据存在当前这个 JVM 的内存里，重启进程就没了，
 * 多个实例部署时各自存各自的。适合验证码这种「丢了重新生成也无所谓」的数据。
 *
 * @Profile("!redis") 的含义：只要激活的 profile 里没有 redis，就装配这个实现。
 * 注意是「!redis」（没有才启用）而不是「dev」——因为除了 redis 之外的所有
 * 环境（本地 dev、将来可能的 test/prod 单机版）都应该用本地缓存，
 * 写成「!redis」就不用每加一个环境都回来补一行。
 */
@Service
@Profile("!redis")
public class CaffeineCacheServiceImpl implements CacheService {

    private final Cache<String, Entry> cache;

    public CaffeineCacheServiceImpl() {
        this.cache = Caffeine.<String, Entry>newBuilder()
                // 条数上限：防止有人拿它当数据库用把内存撑爆。
                // 超过 1 万条时会按「最近最少用」淘汰旧数据
                .maximumSize(10_000)
                // Caffeine 官方 API 是「统一过期时间」，但我们每个 put 想带不同的 TTL，
                // 所以用底层的 Expiry 接口自定义：过期时长从存进去的对象身上现读。
                // 这就像储物柜：Caffeine 只负责到点清柜子，柜子几号到期是存东西时写的
                .expireAfter(new Expiry<String, Entry>() {

                    @Override
                    public long expireAfterCreate(String key, Entry entry, long currentTime) {
                        return entry.ttlNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, Entry entry, long currentTime, long currentDuration) {
                        // 覆盖写：按新数据的 TTL 重新计时
                        return entry.ttlNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, Entry entry, long currentTime, long currentDuration) {
                        // 读不改剩余寿命：验证码「从生成起 5 分钟内有效」，
                        // 不能因为中间看了一眼就续命
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        cache.put(key, new Entry(value, ttl.toNanos()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Entry entry = cache.getIfPresent(key);
        // 泛型擦除后 T 是什么类型由调用方的目标变量决定，
        // 存进去什么、取出来就转回什么（比如存 String 取成 Integer 会炸，
        // 这层类型正确性靠调用方自己保证——本地缓存没法像 Redis 那样吞掉类型）
        return entry == null ? null : (T) entry.value();
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    /** 值 + 这条数据要活多久，捆在一起作为缓存的「一格」 */
    private record Entry(Object value, long ttlNanos) {
    }

}
