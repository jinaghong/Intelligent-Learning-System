package com.hjh.tlias;

import com.hjh.tlias.cache.CacheService;
import com.hjh.tlias.cache.CaffeineCacheServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * P1b CacheService 的验收测试（先写、先跑红，再实现）。
 *
 * 和别的测试不同，这个测试不需要启动 Spring、不需要数据库——
 * CacheService 就是一个纯内存小工具，直接 new 出来就能测。
 * 这种「被测对象不依赖框架」的测试写起来最快、跑起来也最快。
 */
class CacheServiceTest {

    /** 直接造实现类的实例。它不需要任何外部资源（Caffeine 纯内存） */
    private final CacheService cache = new CaffeineCacheServiceImpl();

    @Test
    @DisplayName("1. put 进去 get 出来：原样拿回")
    void putThenGet() {
        cache.put("name", "学习助手", Duration.ofMinutes(5));

        assertEquals("学习助手", cache.get("name"));
    }

    @Test
    @DisplayName("2. 等过了 TTL，数据就取不到了（过期是缓存的核心行为）")
    void entryShouldExpireAfterTtl() throws Exception {
        cache.put("code", "a1b2", Duration.ofMillis(60));

        // TTL 只有 60ms，睡 250ms 必然过期。故意睡远一点（4 倍余量），
        // 避免机器忙的时候刚好卡在边界上导致测试偶发失败
        Thread.sleep(250);

        assertNull(cache.get("code"), "60ms 的 TTL，250ms 后应该已经过期");
    }

    @Test
    @DisplayName("3. evict 之后立刻取不到（主动删除）")
    void evictShouldRemoveImmediately() {
        cache.put("tmp", "x", Duration.ofMinutes(5));
        cache.evict("tmp");

        assertNull(cache.get("tmp"));
    }

    @Test
    @DisplayName("4. 没存过的 key 返回 null，不抛异常")
    void missingKeyShouldReturnNull() {
        assertNull(cache.get("从未存过的key"));
    }

    @Test
    @DisplayName("5. 不只存字符串：任意对象都能存能取")
    void shouldStoreArbitraryObjects() {
        cache.put("list", List.of("a", "b"), Duration.ofMinutes(5));

        assertEquals(List.of("a", "b"), cache.get("list"));
    }

}
