package com.hjh.tlias.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功后返回给前端的内容。
 *
 * ⚠️ 这里刻意没有 password 字段。返回对象是「前端看得见的东西」，
 * 凡是前端不需要的、敏感的字段都不该出现在这里。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private Long id;

    private String username;

    private String name;

    /** 后续请求都要带在请求头里的凭证 */
    private String token;

}
