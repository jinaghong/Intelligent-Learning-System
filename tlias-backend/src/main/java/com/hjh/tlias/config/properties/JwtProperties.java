package com.hjh.tlias.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 绑定 application-dev.yml 里 tlias.jwt 那一组配置。
 *
 * 配置项和字段的对应规则是「烤串转驼峰」：
 *   token-name   ->  tokenName
 *   ttl-millis   ->  ttlMillis
 *   secret-key   ->  secretKey
 *
 * 真实密钥不写在提交进 git 的文件里，application-dev.yml 只放 ${JWT_SECRET:} 占位，
 * 真实值在 application-local.yml。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tlias.jwt")
public class JwtProperties {

    /** 签名密钥，HS256 要求至少 32 字节 */
    private String secretKey;

    /** token 有效期，单位毫秒 */
    private long ttlMillis;

    /** 前端存放 token 的请求头名字 */
    private String tokenName;

}
