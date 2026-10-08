package com.hjh.tlias.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hjh.tlias.common.constant.JwtClaimsConstant;
import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.config.properties.JwtProperties;
import com.hjh.tlias.context.BaseContext;
import com.hjh.tlias.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录校验拦截器。
 *
 * 拦截器 = 装在每个接口门口的保安：请求进来先过他这一关，
 * 验过工牌才放行到 Controller。
 *
 * 注意它和过滤器的区别（面试常问）：拦截器是 Spring MVC 的组件，
 * 能拿到「即将执行哪个 Controller 的哪个方法」；过滤器属于 Servlet 规范，
 * 只知道 URL，不知道方法信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 跨域预检请求（OPTIONS）不带自定义请求头，拦它会导致浏览器直接失败
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // 只拦 Controller 方法。静态资源、错误页等不是 HandlerMethod，放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader(jwtProperties.getTokenName());
        if (!StringUtils.hasText(token)) {
            return reject(response, "未登录，请先登录");
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            Object empId = claims.get(JwtClaimsConstant.EMP_ID);
            if (empId == null) {
                return reject(response, "登录凭证无效，请重新登录");
            }
            // 验过了，把工牌内容放进当前线程的储物柜，Controller / Service 里随时可取
            BaseContext.setCurrentId(Long.valueOf(empId.toString()));
            return true;
        } catch (ExpiredJwtException e) {
            return reject(response, "登录已过期，请重新登录");
        } catch (Exception e) {
            log.warn("token 解析失败: {}", e.getMessage());
            return reject(response, "登录凭证无效，请重新登录");
        }
    }

    /**
     * 请求结束（无论成功、异常还是被拦截）都会走到这里。
     * 这里是清空 ThreadLocal 的唯一入口——放在 afterCompletion 而不是 Controller 里，
     * 是因为它能保证「即使业务代码抛异常」也一定执行。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }

    /**
     * 拒绝并返回 401。
     *
     * 这里用 HTTP 401 而不是像业务异常那样返回 200 + code=0，
     * 因为「没登录」属于**协议层**的问题，让前端拦截器能按状态码统一跳出到登录页，
     * 不用在每个接口的回调里判断 code。
     */
    private boolean reject(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Result.error(message));
        return false;
    }

}
