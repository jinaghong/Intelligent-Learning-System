package com.hjh.tlias.cache;

import java.time.Duration;

/**
 * 缓存统一入口。
 *
 * 为什么不直接 import Caffeine 到处用？
 * 因为「缓存」是业务需求，「用 Caffeine 实现」是实现细节。中间加一层接口后：
 *   - 业务代码只认识 put / get / evict 三个方法，将来换成 Redis 不用改一行业务代码
 *   - 两个实现类靠 @Profile 切换：默认（本地开发）用 Caffeine，
 *     部署时激活 redis profile 就切到 Redis
 * 这就是「面向接口编程」最朴素的样子——和 FileStorageService、JwtUtil 是同一个套路。
 */
public interface CacheService {

    /**
     * 存一条数据。
     *
     * @param key   缓存键
     * @param value 任意对象（实现类内部负责序列化/包装）
     * @param ttl   存活时长，超过后这条数据自动消失
     */
    void put(String key, Object value, Duration ttl);

    /**
     * 取一条数据。没存过、或已经过期，都返回 null。
     */
    <T> T get(String key);

    /**
     * 主动删除。比如验证码用过一次就该立刻失效，不该等它自然过期。
     */
    void evict(String key);

}
