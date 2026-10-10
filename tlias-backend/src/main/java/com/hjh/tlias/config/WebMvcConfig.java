package com.hjh.tlias.config;

import com.hjh.tlias.config.properties.StorageProperties;
import com.hjh.tlias.interceptor.JwtTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtTokenInterceptor jwtTokenInterceptor;
    private final StorageProperties storageProperties;

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
                        "/error",          // 出错时的转发，拦了会导致真正的错误信息看不到
                        // 上传的图片要放行：前端把 URL 放进 <img src> 后，
                        // 浏览器加载图片时发的是普通 GET，带不了自定义的 token 请求头。
                        // 不放行的话页面上全是 401 碎图。
                        // 注意只放行「读」，上传接口 POST /upload 不在名单里，依然要登录
                        "/uploads/**"
                );
    }

    /**
     * 静态资源映射：把 URL /uploads/** 映射到本地上传目录。
     *
     * 效果：浏览器访问 /api/uploads/2026/10/10/xxx.png 时，
     * 不经过任何 Controller，Spring 直接去磁盘把文件读出来返回。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 目录转绝对路径再拼「file:」前缀，两个细节都有原因：
        //   1) 相对路径是相对「启动进程的工作目录」解释的——IDEA 直接运行、
        //      mvn 运行、打包后运行，工作目录各不相同，相对路径容易时灵时不灵
        //   2) 末尾必须有 /，否则 /uploads/a/b.png 会被直接拼到目录名后面
        // 反斜杠换成正斜杠：Windows 路径放进资源定位字符串时，正斜杠最稳
        String dir = Path.of(storageProperties.getLocal().getDir())
                .toAbsolutePath()
                .normalize()
                .toString()
                .replace('\\', '/');

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + dir + "/");
    }

}
