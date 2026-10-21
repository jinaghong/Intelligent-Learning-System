package com.hjh.tlias.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hjh.tlias.annotation.Log;
import com.hjh.tlias.context.BaseContext;
import com.hjh.tlias.entity.OperLog;
import com.hjh.tlias.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面（P7 的核心）。
 *
 * 切面 = 「横切关注点」的落点。记录操作日志这件事和每个业务方法都有关、
 * 却又和业务无关——写在每个方法里是几十处重复，写在这里是一处。
 *
 * 三个注解各管一件事：
 *   @Aspect     声明「我是一个切面」
 *   @Component  交给 Spring 管理（切面也得是 bean 才会生效）
 *   @Around     环绕通知：目标方法执行前、执行后、抛异常时，我都在场
 *
 * 切点表达式 "@annotation(log)" 的含义：凡是贴着 @Log 注解的方法，
 * 都交给我环绕；同时把那个注解实例绑定到参数 log 上（本类没用到注解属性，
 * 但这么写是惯例，将来给 @Log 加属性就不用改表达式）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final LogService logService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(logAnn)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnn) throws Throwable {
        long begin = System.currentTimeMillis();

        OperLog operLog = new OperLog();
        operLog.setEmpId(BaseContext.getCurrentId());
        operLog.setOperateTime(LocalDateTime.now());
        // 用方法签名上的声明类型，而不是 joinPoint.getTarget().getClass()——
        // 后者在类被代理时会拿到一长串带 $$ 的代理类名，前者永远是你写的那个类
        operLog.setClassName(joinPoint.getSignature().getDeclaringType().getName());
        operLog.setMethodName(joinPoint.getSignature().getName());
        operLog.setMethodParams(serializeArgs(joinPoint.getArgs()));
        operLog.setIp(currentIp());

        try {
            // proceed() 才是「真正去执行目标方法」。切面拿到的是它的返回值
            Object result = joinPoint.proceed();
            operLog.setStatus(1);
            // 返回值也要脱敏：登录接口的返回体里带着 JWT，原样落库等于把凭证写进审计表
            operLog.setReturnValue(serializeMasked(result));
            return result;
        } catch (Throwable e) {
            // 失败也要记（审计的意义就在于失败的操作也有据可查），
            // 记完原样往上抛——切面吞掉异常的话，全局异常处理器就接不到了
            operLog.setStatus(0);
            operLog.setErrorMsg(e.getMessage());
            throw e;
        } finally {
            operLog.setCostTime(System.currentTimeMillis() - begin);
            try {
                logService.record(operLog);
            } catch (Exception ex) {
                // 日志写库失败（比如表被误删）不能把业务搞挂：
                // 审计丢了可以补，业务挂了是事故。降级为打一行 error 日志
                LogAspect.log.error("操作日志写入失败: {}.{}", operLog.getClassName(),
                        operLog.getMethodName(), ex);
            }
        }
    }

    /**
     * 把方法入参序列化成 JSON 数组字符串。
     *
     * 两类东西不能进日志：
     *   1. MultipartFile / HttpServletRequest / Response / BindingResult——
     *      要么序列化不了，要么会把整个文件字节塞进 TEXT 列，直接跳过
     *   2. 密码——序列化后递归扫描 JSON 树，password 字段一律替换成 ******，
     *      否则新增员工 / 登录的明文密码就落进日志表了（安全红线）
     */
    private String serializeArgs(Object[] args) {
        List<Object> kept = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof MultipartFile
                    || arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof BindingResult) {
                continue;
            }
            kept.add(arg);
        }
        try {
            JsonNode tree = objectMapper.valueToTree(kept);
            maskSensitiveFields(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (Exception e) {
            // 序列化失败不该影响业务，留一个标记说明「这一行没记下参数」
            return "[参数序列化失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 返回值脱敏后再落库。
     *
     * 和入参一样：序列化失败不能反过来把业务搞挂，所以单独兜底成一行标记。
     */
    private String serializeMasked(Object value) {
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            maskSensitiveFields(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (Exception e) {
            return "[返回值序列化失败: " + e.getMessage() + "]";
        }
    }

    /** 递归遍历 JSON 树，把密码与 token 的值替换成 ****** */
    private void maskSensitiveFields(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            if (obj.has("password")) {
                obj.put("password", "******");
            }
            if (obj.has("token")) {
                obj.put("token", "******");
            }
            obj.properties().forEach(entry -> maskSensitiveFields(entry.getValue()));
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(this::maskSensitiveFields);
        }
    }

    /**
     * 拿当前请求的客户端 IP。
     *
     * RequestContextHolder 是 Spring 存在当前线程上的「请求上下文」，
     * 和 BaseContext 一样靠 ThreadLocal——所以这段代码只在请求线程里有效。
     * （这正是「异步写日志」要先把 IP 和 empId 取出来再提交任务的原因。）
     */
    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }

}
