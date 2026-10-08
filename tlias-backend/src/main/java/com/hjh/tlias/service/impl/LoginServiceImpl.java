package com.hjh.tlias.service.impl;

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

        // ==================================================================
        //  TODO —— 这一段由你来写（P2 的核心业务逻辑）
        //
        //  做法：把下面那行抛异常删掉，按这五步实现。
        //
        //  第 1 步  按用户名查出员工
        //           提示：empMapper.selectOne(new LambdaQueryWrapper<Emp>()
        //                      .eq(Emp::getUsername, loginDTO.getUsername()))
        //           想想：查不到时会返回什么？是抛异常还是返回 null？
        //
        //  第 2 步  员工不存在  或  密码对不上  → 都抛出同一个异常
        //               throw new BusinessException("用户名或密码错误");
        //           ⚠️ 两句话必须一模一样。如果提示不同，攻击者就能靠试错
        //              反推出「哪些用户名是真实存在的」，这叫用户名枚举漏洞。
        //
        //  第 3 步  校验密码用 passwordEncoder.matches(明文, 密文)
        //           ⚠️ 绝对不能用 equals 比较！库里存的是 BCrypt 密文，
        //              而且同一个人每次加密结果都不同（因为盐是随机的），
        //              字符串比较永远返回 false。
        //
        //  第 4 步  签发 token
        //           提示：jwtUtil.createToken(Map.of(JwtClaimsConstant.EMP_ID, emp.getId()))
        //
        //  第 5 步  组装并返回 LoginVO
        //           提示：LoginVO.builder().id(...).username(...).name(...).token(...).build()
        //
        //  写完跑 mvn test，LoginFlowTest 里的 6 项会告诉你做对没有。
        // ==================================================================
        throw new UnsupportedOperationException("P2 登录逻辑待实现 —— 见上方 TODO 注释");
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
