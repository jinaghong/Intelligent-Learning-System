package com.hjh.tlias.common.exception;

import com.hjh.tlias.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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

    /**
     * 参数校验失败：@Valid 校验 @RequestBody 没过时抛的就是这个异常。
     *
     * 和上面两个同理——它是「用户填错了」，不是「服务器炸了」。
     * 不单独接住就会掉进兜底处理器，前端收到「系统繁忙，请稍后重试」，
     * 用户完全不知道该改哪里。所以要把字段上的提示原文透出去。
     *
     * 一个 DTO 可能好几个字段同时不合格，这里只取第一条：
     * 让用户一次改一项，比糊一大串更清楚。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("请求参数不合法");
        log.warn("参数校验失败: {}", message);
        return Result.error(message);
    }

    /**
     * 参数绑定校验失败：@Valid 标在「不是 @RequestBody 的参数」上时（比如 GET 请求的
     * 查询串绑定到 DTO），校验没过抛的是这个异常。
     *
     * 它和上面的 MethodArgumentNotValidException 是父子关系（后者是子类、专管请求体），
     * Spring 会挑更具体的处理器，两个共存不会打架。
     * 不接住它会掉进兜底分支显示「系统繁忙」——用户只是把页码传成了 0，
     * 却被告知服务器繁忙，和当初 404/405 被兜底揽走是同一类问题。
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse("请求参数不合法");
        log.warn("参数绑定校验失败: {}", message);
        return Result.error(message);
    }

    /**
     * 上传文件超过大小限制：请求还没走到 Controller，Tomcat 在解析 multipart 时
     * 会发现 part 超限并拒绝，Spring 把它包装成 MaxUploadSizeExceededException。
     *
     * 不接住的话会掉进兜底处理器显示「系统繁忙」——用户只是传了张大图，
     * 和 404/405/参数校验被兜底揽走是同一类问题，必须单独给一句人话。
     *
     * 提示里的 2MB 和 application.yml 里 spring.servlet.multipart.max-file-size
     * 是同一个限制的两处表述，改限制时两处要一起改。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("上传文件超过大小限制: {}", e.getMessage());
        return Result.error("文件大小不能超过 2MB");
    }

    /** 兜底：到这里说明是没预料到的程序错误，日志要打全栈，但对外只说一句话 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统繁忙，请稍后重试");
    }

}
