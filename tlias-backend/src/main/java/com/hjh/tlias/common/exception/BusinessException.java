package com.hjh.tlias.common.exception;

/**
 * 业务异常。
 *
 * 什么时候用它：当程序没出错、但业务规则不允许时。
 * 例如「删除部门时发现该部门下还有员工」——这不是程序 bug，
 * 是业务上讲不通，所以抛这个异常，由 GlobalExceptionHandler 转成友好提示。
 *
 * 继承 RuntimeException 而不是 Exception，是为了不用在每个方法签名上
 * 声明 throws，调用方也不用层层 try/catch。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

}
