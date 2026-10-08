package com.hjh.tlias.common.exception;

import com.hjh.tlias.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * 作用：Service / Controller 里抛出的异常都会被这里接住，转成统一的 Result，
 * 业务代码里就不用写 try/catch 了。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：程序没错，是业务规则不允许，把原因原样告诉前端 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    /** 唯一键冲突：新增部门重名、新增员工用户名重复等，都是从这里冒出来的 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("唯一键冲突: {}", e.getMessage());
        return Result.error("数据已存在，请勿重复添加");
    }

    /**
     * 下面两个是「不处理会更糟」的情况。
     *
     * 最后的兜底处理器接的是 Exception，会把所有异常都揽过来。
     * 但「访问了不存在的接口」和「用错请求方法」在 Spring 里也是异常，
     * 如果被兜底处理器接走，前端会收到 200 + "系统繁忙"，
     * 明明是自己路径写错了，却看起来像服务器挂了，排查会绕大弯。
     * 所以单独接出来，给出能直接定位问题的提示。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("接口不存在: {}", e.getResourcePath());
        return Result.error("接口不存在，请检查请求路径");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.error("请求方法不支持，请检查该用 GET 还是 POST");
    }

    /** 兜底：到这里说明是没预料到的程序错误，日志要打全栈，但对外只说一句话 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统繁忙，请稍后重试");
    }

}
