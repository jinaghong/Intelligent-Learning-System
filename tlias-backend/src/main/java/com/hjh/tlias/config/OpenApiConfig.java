package com.hjh.tlias.config;

import com.hjh.tlias.config.properties.JwtProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {

    private static final String TOKEN_SCHEME_NAME = "tliasToken";

    private final JwtProperties jwtProperties;

    @Bean
    public OpenAPI tliasOpenApi() {
        // Swagger UI 右上角那个 Authorize 按钮，就是靠下面这段长出来的。
        //
        // 没有它，网页上没法给请求加自定义请求头，所有受保护接口都只能测出 401。
        // 因为本项目的 token 走的是自定义头 tlias-token，不是标准的
        // Authorization: Bearer 写法。
        //
        // 类型选 APIKEY + in: HEADER，意思是「一枚放在请求头里的令牌」。
        // 这里描述的是「怎么传」而不是「令牌长什么样」，所以 JWT 用它建模不算错。
        SecurityScheme tokenScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(jwtProperties.getTokenName())
                .description("填登录接口返回的 token 原文，不要加 Bearer 前缀");

        return new OpenAPI()
                .info(new Info()
                        .title("tlias 智能学习辅助系统 API")
                        .version("1.0.0")
                        .description("部门 / 员工 / 班级 / 学员 / 课程 / 数据统计 / AI 增强"))
                .components(new Components().addSecuritySchemes(TOKEN_SCHEME_NAME, tokenScheme))
                // 全局挂上安全要求：所有接口都显示需要 token。
                // /login 和 /health 其实不需要，会多显示一把小锁，但点了 Authorize
                // 照样能调通，不影响功能，比起逐个接口标注省事得多。
                .addSecurityItem(new SecurityRequirement().addList(TOKEN_SCHEME_NAME));
    }

}
