package com.hjh.tlias.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 绑定 application-dev.yml 里 storage 那一组配置。
 *
 * 嵌套类的绑定规则：storage.local.dir 对应「StorageProperties.local.dir」——
 * 点号按层级拆开，storage 后面的 local 匹配嵌套类里的字段名（烤串转驼峰照旧）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private Local local = new Local();

    @Data
    public static class Local {

        /** 文件落盘目录。写相对路径时相对于「启动进程的工作目录」 */
        private String dir;

        /**
         * 拼返回 URL 用的前缀，形如 http://localhost:8080/api。
         * 这里要带 context-path（/api），因为前端拿到的 URL 是要能直接访问的完整地址。
         */
        private String baseUrl;

    }

}
