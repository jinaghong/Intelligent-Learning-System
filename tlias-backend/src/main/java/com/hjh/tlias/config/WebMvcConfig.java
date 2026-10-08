package com.hjh.tlias.config;

import com.hjh.tlias.interceptor.JwtTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtTokenInterceptor jwtTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenInterceptor)
                // 默认全拦，再一个个开口放行。
                // 反过来写（只拦已知的业务路径）容易漏——新加的接口忘了登记就变成裸奔
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",          // 登录本身当然不能要求先登录
                        "/health",         // 健康检查给监控用，不该要凭证
                        "/v3/api-docs/**", // Swagger 的接口文档数据
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/error"           // 出错时的转发，拦了会导致真正的错误信息看不到
                );
    }

}
