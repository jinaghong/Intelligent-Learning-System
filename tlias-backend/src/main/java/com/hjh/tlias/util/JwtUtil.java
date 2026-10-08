package com.hjh.tlias.util;

import com.hjh.tlias.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 的生成与解析。
 *
 * JWT 是什么：一张**服务器签过字的电子工牌**。
 *   - 工牌上写着「我是 7 号员工」（这部分是明文，谁都能看，所以绝不能放密码）
 *   - 末尾有一串用密钥算出来的签名（谁都能验，但没有密钥就伪造不出来）
 * 服务器本身不保存这张工牌，验签通过就认人。这就是「无状态」：
 * 重启服务器、扩展到多台机器，都不影响已有工牌继续有效。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    /** 生成 token。claims 里放你想带的信息，这里只放 empId */
    public String createToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getTtlMillis());

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey())
                .compact();
    }

    /**
     * 解析并验签。
     * token 被篡改 / 格式不对 / 已过期，都会抛异常，由调用方决定怎么处理。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

}
