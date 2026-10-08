package com.hjh.tlias.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tliasOpenApi() {
        return new OpenAPI().info(new Info()
                .title("tlias 智能学习辅助系统 API")
                .version("1.0.0")
                .description("部门 / 员工 / 班级 / 学员 / 课程 / 数据统计 / AI 增强"));
    }

}
