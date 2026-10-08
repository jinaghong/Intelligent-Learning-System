package com.hjh.tlias.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjh.tlias.common.constant.JwtClaimsConstant;
import com.hjh.tlias.common.exception.BusinessException;
import com.hjh.tlias.context.BaseContext;
import com.hjh.tlias.dto.LoginDTO;
import com.hjh.tlias.entity.Emp;
import com.hjh.tlias.mapper.EmpMapper;
import com.hjh.tlias.service.LoginService;
import com.hjh.tlias.util.JwtUtil;
import com.hjh.tlias.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    // 三个依赖都已经注入好了，直接用即可。
    // 用构造器注入（final 字段 + RequiredArgsConstructor）而不是 @Autowired 标在字段上：
    // 一能保证依赖不为 null，二能让这个类在单元测试里直接 new 出来，不必启动 Spring
    private final EmpMapper empMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginDTO loginDTO) {

        // 第 1 步：按用户名查员工。
        // selectOne 查不到时返回 null（不抛异常），所以下一步必须判空。
        Emp emp = empMapper.selectOne(new LambdaQueryWrapper<Emp>()
                .eq(Emp::getUsername, loginDTO.getUsername()));

        // 第 2 步：员工不存在 或 密码对不上 → 抛同一个异常，提示一字不差。
        // 只要两条提示有差别，攻击者就能靠试错反推出「哪些用户名真实存在」，
        // 这叫用户名枚举漏洞。
        //
        // 第 3 步：校验密码只能用 passwordEncoder.matches(明文, 密文)，绝不能用
        // equals——库里存的是 BCrypt 密文，且盐随机会导致每次加密结果都不同，
        // 字符串比较永远返回 false。
        // || 的短路特性保证 emp 为 null 时不会继续执行 emp.getPassword()。
        if (emp == null || !passwordEncoder.matches(loginDTO.getPassword(), emp.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 第 4 步：签发 token，载荷里只放 empId。
        // token 的载荷是明文，谁都能看，绝不能往里放密码之类的东西。
        String token = jwtUtil.createToken(Map.of(JwtClaimsConstant.EMP_ID, emp.getId()));

        // 第 5 步：组装 LoginVO（刻意不含 password 字段）返回。
        return LoginVO.builder()
                .id(emp.getId())
                .username(emp.getUsername())
                .name(emp.getName())
                .token(token)
                .build();
    }

    @Override
    public Emp getCurrentEmp() {
        // 这个方法的逻辑我写好了，作用是给你示范 BaseContext 该怎么用：
        // 拦截器已经把 empId 放进当前线程的储物柜，这里直接取就行，
        // 不需要在方法参数里传 empId。
        Long empId = BaseContext.getCurrentId();
        return empMapper.selectById(empId);
    }

}
