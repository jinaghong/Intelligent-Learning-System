package com.hjh.tlias.common.result;

import lombok.Data;

/**
 * 统一响应体。所有接口都返回这个结构，前端只需按同一套字段解析。
 *
 * code: 1 = 成功，0 = 失败
 */
@Data
public class Result<T> {

    private Integer code;

    private String msg;

    private T data;

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.code = 1;
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = 1;
        result.data = data;
        return result;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.code = 0;
        result.msg = msg;
        return result;
    }

}
