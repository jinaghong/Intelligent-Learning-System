package com.hjh.tlias.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.hjh.tlias.config.properties.AliyunOssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * OSS 客户端的手工装配。
 *
 * 为什么不用 @Component 直接建：OSSClient 要读 AliyunOssProperties 里的四个值才能 new，
 * 属于「带参数的对象」，放到 @Configuration 里用 @Bean 方法最顺手；
 * 而且只有激活 oss profile 时才需要它，@Profile 在这里一把说清。
 *
 * destroyMethod = "shutdown"：OSSClient 内部维护着 HTTP 连接池，
 * Spring 关闭容器时会自动调它的 shutdown() 把连接优雅放掉——
 * 不写这个的话进程退出时连接池会被硬切，控制台会冒出烦人的警告。
 */
@Configuration
@Profile("oss")
@RequiredArgsConstructor
public class StorageConfig {

    private final AliyunOssProperties ossProperties;

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient() {
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret());
    }

}
