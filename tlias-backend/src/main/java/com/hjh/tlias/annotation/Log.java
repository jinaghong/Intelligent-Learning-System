package com.hjh.tlias.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。贴在 Controller 方法上，LogAspect 就会在方法执行前后自动记录：
 * 谁调的、调了哪个方法、传了什么参、花了多少毫秒、成功还是失败。
 *
 * 自定义注解的三要素（这个文件全占了）：
 *   @Target(METHOD)        只能贴在方法上，贴到类上编译就报错
 *   @Retention(RUNTIME)    保留到运行期——切面是运行时才去读它的，
 *                          若用默认的 CLASS 保留级别，运行时注解就被擦掉了
 *   @interface             声明注解的关键字（注意带 @）
 *
 * 它本身没有任何属性、没有任何逻辑，只是一个「标签」——真正干活的是 LogAspect。
 * 这也是注解的本质：给代码贴标签，让别的代码（切面）能认出「这是要处理的」。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
}
