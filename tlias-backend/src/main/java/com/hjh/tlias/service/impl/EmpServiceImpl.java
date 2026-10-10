package com.hjh.tlias.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjh.tlias.common.exception.BusinessException;
import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.dto.EmpDTO;
import com.hjh.tlias.dto.EmpExperienceDTO;
import com.hjh.tlias.dto.EmpQueryDTO;
import com.hjh.tlias.entity.Emp;
import com.hjh.tlias.entity.EmpExperience;
import com.hjh.tlias.mapper.EmpExperienceMapper;
import com.hjh.tlias.mapper.EmpMapper;
import com.hjh.tlias.service.EmpService;
import com.hjh.tlias.vo.EmpDetailVO;
import com.hjh.tlias.vo.EmpExportVO;
import com.hjh.tlias.vo.EmpVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmpServiceImpl implements EmpService {

    private final EmpMapper empMapper;
    private final EmpExperienceMapper empExperienceMapper;

    /** 新增 / 改密码时都要用它把明文转成 BCrypt 密文，绝不能把明文存进库 */
    private final PasswordEncoder passwordEncoder;

    // ------------------------------------------------------------------
    //  查询
    // ------------------------------------------------------------------

    @Override
    public PageResult<EmpVO> page(EmpQueryDTO query) {
        // Page 是 IPage 的实现类，两个参数是「第几页」和「每页几条」。
        // 它本身只是个空壳子——真正干活的是分页插件，它会：
        //   1. 把 LIMIT 塞进我们的 SQL 里查这一页
        //   2. 另跑一条 COUNT 查总数
        //   3. 把两个结果都写回这个 page 对象
        Page<EmpVO> page = new Page<>(query.getPage(), query.getPageSize());

        IPage<EmpVO> result = empMapper.selectPageWithDept(page, query);
        return PageResult.from(result);
    }

    @Override
    public EmpDetailVO getDetailById(Long id) {
        EmpDetailVO detail = empMapper.selectDetailById(id);
        if (detail == null) {
            throw new BusinessException("员工不存在");
        }
        // 工作经历是另一张表，单独查一次。这里不需要考虑 N+1 问题：
        // 只有「查一个员工」这一种场景才走这里，永远只多一条 SQL
        detail.setExprList(listExperiences(id));
        return detail;
    }

    // ------------------------------------------------------------------
    //  新增
    // ------------------------------------------------------------------

    /**
     * 新增员工 + 工作经历。
     *
     * ⚠️ @Transactional 不能省：这个方法要往两张表写。如果员工插入成功、
     * 工作经历插入时出错，没有事务的话员工就留在库里了，变成一条
     * 「有资料但没经历」的脏数据，而前端收到的是失败——用户重试一次
     * 就冒出第二个同名员工（然后被用户名校验拦住，彻底卡死）。
     * 加了事务，两步要么都成，要么都不算。
     */
    @Override
    @Transactional
    public void save(EmpDTO empDTO) {
        if (empMapper.selectCount(new LambdaQueryWrapper<Emp>()
                .eq(Emp::getUsername, empDTO.getUsername())) > 0) {
            throw new BusinessException("用户名已存在");
        }
        // 新增必须给密码。这条规则没法用注解表达（修改时可以不传），
        // 所以只能写在这里
        if (empDTO.getPassword() == null || empDTO.getPassword().isBlank()) {
            throw new BusinessException("密码不能为空");
        }

        Emp emp = new Emp();
        emp.setUsername(empDTO.getUsername());
        // 存进去的是密文。BCrypt 每次加密同一个密码结果都不同（盐是随机的），
        // 所以校验时只能用 passwordEncoder.matches()，不能用字符串比较
        emp.setPassword(passwordEncoder.encode(empDTO.getPassword()));
        emp.setName(empDTO.getName());
        emp.setGender(empDTO.getGender());
        emp.setImage(empDTO.getImage());
        emp.setJob(empDTO.getJob());
        emp.setEntryDate(empDTO.getEntryDate());
        emp.setDeptId(empDTO.getDeptId());
        // status 没传就默认在职（1）。数据库那一列是 NOT NULL，
        // 直接留 null 会报错，与其让用户看到「系统繁忙」，不如给个合理默认值
        emp.setStatus(empDTO.getStatus() == null ? 1 : empDTO.getStatus());

        empMapper.insert(emp);
        // insert 之后 emp.getId() 里已经有了数据库生成的自增主键
        //（MyBatis-Plus 用 useGeneratedKeys 回填的）
        saveExperiences(emp.getId(), empDTO.getExprList());
    }

    // ------------------------------------------------------------------
    //  修改
    // ------------------------------------------------------------------

    /**
     * 修改员工 + 整体替换工作经历。
     *
     * 同样需要 @Transactional —— 里面的「先删后插」两步是拆不开的，
     * 详见下面 replaceExperiences 的注释。
     */
    @Override
    @Transactional
    public void update(EmpDTO empDTO) {
        if (empMapper.selectById(empDTO.getId()) == null) {
            throw new BusinessException("员工不存在");
        }
        // 换用户名时也要保证不撞车。.ne(Emp::getId, ...) 把自己排除掉，
        // 否则「用户名原样提交一遍」会被自己判成重名
        if (empMapper.selectCount(new LambdaQueryWrapper<Emp>()
                .eq(Emp::getUsername, empDTO.getUsername())
                .ne(Emp::getId, empDTO.getId())) > 0) {
            throw new BusinessException("用户名已存在");
        }

        Emp emp = new Emp();
        emp.setId(empDTO.getId());
        emp.setUsername(empDTO.getUsername());
        emp.setName(empDTO.getName());
        emp.setGender(empDTO.getGender());
        emp.setImage(empDTO.getImage());
        emp.setJob(empDTO.getJob());
        emp.setEntryDate(empDTO.getEntryDate());
        emp.setDeptId(empDTO.getDeptId());
        emp.setStatus(empDTO.getStatus());

        // 密码这一列特殊处理：只有这次传了非空密码才改。
        // 不传就不 set（保持 null），而 updateById 只更新非 null 的字段，
        // 于是这条 SQL 里根本不会出现 password，老密码原封不动。
        // 这正是「修改页不填密码」能正常工作的原因
        if (empDTO.getPassword() != null && !empDTO.getPassword().isBlank()) {
            emp.setPassword(passwordEncoder.encode(empDTO.getPassword()));
        }

        empMapper.updateById(emp);
        replaceExperiences(empDTO.getId(), empDTO.getExprList());
    }

    // ------------------------------------------------------------------
    //  删除
    // ------------------------------------------------------------------

    /**
     * 批量删除员工。
     *
     * 不需要 @Transactional：只有一条 DELETE。两张表上关联的数据由数据库的
     * 外键自动处理 —— emp_experience 那条外键是 ON DELETE CASCADE，
     * 员工的经历会跟着删掉；clazz.master_id 那条是 ON DELETE SET NULL，
     * 如果这个员工正好是某个班的班主任，那个班的班主任会被自动置空。
     */
    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的员工");
        }
        empMapper.deleteByIds(ids);
    }

    // ------------------------------------------------------------------
    //  导出
    // ------------------------------------------------------------------

    @Override
    public List<EmpExportVO> listForExport(EmpQueryDTO query) {
        return empMapper.selectListWithDept(query).stream()
                .map(this::toExportVO)
                .toList();
    }

    /**
     * EmpVO -> EmpExportVO，把库里的编码翻成中文。
     *
     * 为什么不直接把编码写进 Excel？因为导出的表格是给人看的。
     * 「性别」那一列出现 1 和 2，拿到表的人还得回头查文档。
     */
    private EmpExportVO toExportVO(EmpVO emp) {
        EmpExportVO vo = new EmpExportVO();
        vo.setUsername(emp.getUsername());
        vo.setName(emp.getName());
        vo.setGender(text(emp.getGender(), 1, "男", "女"));
        vo.setJob(jobText(emp.getJob()));
        // LocalDate.toString() 本来就是 ISO 的 yyyy-MM-dd，直接用它
        vo.setEntryDate(emp.getEntryDate() == null ? "" : emp.getEntryDate().toString());
        // 没有部门的员工，这一格留空，不要写 "null"
        vo.setDeptName(emp.getDeptName() == null ? "" : emp.getDeptName());
        vo.setStatus(text(emp.getStatus(), 1, "在职", "离职"));
        return vo;
    }

    /** 两选一的编码翻译，比如 1=男 2=女 */
    private String text(Integer code, int firstCode, String firstText, String secondText) {
        if (code == null) {
            return "";
        }
        return code == firstCode ? firstText : secondText;
    }

    /**
     * 职位编码翻译。
     *
     * 用 switch 表达式而不是一长串 if-else —— Java 14 之后 switch 可以直接
     * 产出值，每个分支用 -> 不用写 break，编译器还会检查有没有漏掉分支。
     * default 留着，否则将来数据库里冒出个 6，导出就直接抛异常了。
     */
    private String jobText(Integer job) {
        if (job == null) {
            return "";
        }
        return switch (job) {
            case 1 -> "班主任";
            case 2 -> "讲师";
            case 3 -> "学工主管";
            case 4 -> "教研主管";
            case 5 -> "咨询师";
            default -> "未知职位(" + job + ")";
        };
    }

    // ------------------------------------------------------------------
    //  私有工具：工作经历
    // ------------------------------------------------------------------

    private List<EmpExperience> listExperiences(Long empId) {
        return empExperienceMapper.selectList(new LambdaQueryWrapper<EmpExperience>()
                .eq(EmpExperience::getEmpId, empId)
                .orderByAsc(EmpExperience::getBeginDate));
    }

    /**
     * 把工作经历改成「这次提交的这一份」，做法是先删后插。
     *
     * 为什么不逐条比对差异（哪条新增、哪条改了、哪条删了）？
     * 因为那要写三段逻辑、还得给前端传每条经历的主键，而工作经历这种数据
     * 一次也就几条，全删重插的成本可以忽略。用简单换可靠。
     *
     * 代价是**必须和调用方在同一个事务里**：先删成功后插失败的话，
     * 这段经历就凭空消失了，用户还以为只是保存失败。
     */
    private void replaceExperiences(Long empId, List<EmpExperienceDTO> exprList) {
        empExperienceMapper.delete(new LambdaQueryWrapper<EmpExperience>()
                .eq(EmpExperience::getEmpId, empId));
        saveExperiences(empId, exprList);
    }

    /** 把 DTO 列表逐条转成实体插进去；empId 由这里统一填，不信任前端传的值 */
    private void saveExperiences(Long empId, List<EmpExperienceDTO> exprList) {
        if (exprList == null || exprList.isEmpty()) {
            return;
        }
        for (EmpExperienceDTO dto : exprList) {
            EmpExperience expr = new EmpExperience();
            expr.setEmpId(empId);
            expr.setBeginDate(dto.getBeginDate());
            expr.setEndDate(dto.getEndDate());
            expr.setCompany(dto.getCompany());
            expr.setJob(dto.getJob());
            empExperienceMapper.insert(expr);
        }
    }

}
