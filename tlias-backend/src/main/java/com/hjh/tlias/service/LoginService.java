package com.hjh.tlias.service;

import com.hjh.tlias.dto.LoginDTO;
import com.hjh.tlias.entity.Emp;
import com.hjh.tlias.vo.LoginVO;

public interface LoginService {

    /** 登录：校验用户名密码，成功则签发 token */
    LoginVO login(LoginDTO loginDTO);

    /** 取当前登录员工的信息（从 ThreadLocal 上下文里读 id） */
    Emp getCurrentEmp();

}
