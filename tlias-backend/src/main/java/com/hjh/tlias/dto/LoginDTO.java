package com.hjh.tlias.dto;

import lombok.Data;

/**
 * 登录请求体。前端 POST /api/login 时提交的 JSON 会映射成这个对象。
 *
 * DTO 和 Entity 为什么要分开：Entity 是数据库表的镜像，字段跟表一一对应；
 * DTO 是「这一次请求想要什么」的形状。两者混用会出问题——
 * 比如用 Emp 直接接登录请求，前端就能顺便提交 status、deptId 等他不该控制的字段。
 */
@Data
public class LoginDTO {

    private String username;

    private String password;

}
