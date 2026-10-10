package com.hjh.tlias.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 绑定 application-local.yml 里 aliyun.oss 那一组配置。
 *
 * 四个值的来历（都从阿里云 OSS 控制台可以查到）：
 *   access-key-id / access-key-secret  RAM 用户的访问密钥，相当于账号密码
 *   bucket-name                        bucket 名字，比如 tlias-avatar
 *   endpoint                           这个 bucket 所在区域的接入点，比如 oss-cn-chengdu.aliyuncs.com
 *
 * 密钥属于敏感信息，只写在 application-local.yml（已在 .gitignore 里），
 * 提交到 git 的 application-local.yml.example 里只有空占位。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

    private String accessKeyId;

    private String accessKeySecret;

    private String bucketName;

    private String endpoint;

}
