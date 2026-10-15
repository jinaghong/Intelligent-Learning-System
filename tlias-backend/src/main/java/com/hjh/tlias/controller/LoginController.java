package com.hjh.tlias.controller;

import com.hjh.tlias.annotation.Log;
import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.dto.LoginDTO;
import com.hjh.tlias.entity.Emp;
import com.hjh.tlias.service.LoginService;
import com.hjh.tlias.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "登录认证")
public class LoginController {

    private final LoginService loginService;

    // 登录成功失败都记：失败日志带 IP，是「有人在爆破密码」的第一手证据。
    // 注意此时 emp_id 必为 NULL——登录这个接口本来就是「还没有身份」的人在调
    @Log
    @PostMapping("/login")
    @Operation(summary = "员工登录")
    public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        return Result.success(loginService.login(loginDTO));
    }

    /**
     * 登出。
     *
     * 注意这个接口其实没什么可做的：JWT 是「无状态」的，
     * token 签出去之后服务器并没有保存它，所以服务端也没有东西可以「注销」。
     * 真正的登出动作是**前端把存在浏览器里的 token 删掉**。
     *
     * 那为什么还要留这个接口？两个原因：
     *   1. 给前端一个明确的动作可以调用，语义清晰
     *   2. 将来若要支持「强制下线」（改密码后让旧 token 立即失效），
     *      就需要在这里往 Redis 里写一份黑名单——那时这个接口就有了实质内容
     */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public Result<Void> logout() {
        return Result.success();
    }

    @GetMapping("/login/me")
    @Operation(summary = "获取当前登录员工的信息")
    public Result<Emp> me() {
        return Result.success(loginService.getCurrentEmp());
    }

}
