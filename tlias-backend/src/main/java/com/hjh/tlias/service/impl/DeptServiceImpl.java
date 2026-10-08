package com.hjh.tlias.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjh.tlias.common.exception.BusinessException;
import com.hjh.tlias.dto.DeptDTO;
import com.hjh.tlias.entity.Dept;
import com.hjh.tlias.entity.Emp;
import com.hjh.tlias.mapper.DeptMapper;
import com.hjh.tlias.mapper.EmpMapper;
import com.hjh.tlias.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 部门管理的业务实现。
 *
 * 这个类的三条规矩：
 *   1. 只做「业务判断」，不写 SQL —— SQL 由 Mapper 生成
 *   2. 只抛 BusinessException —— 没人写 try/catch，全局异常处理器统一转成 Result
 *   3. 每条业务规则都要显式表达，不靠数据库约束「顺带」帮你报错
 */
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private final DeptMapper deptMapper;

    /**
     * 删部门前要数这个部门里有几个员工，所以这里得用到员工的 Mapper。
     * 这不算「跨模块乱调」——「部门还能不能删」本来就是一条跨两张表的业务规则，
     * 放在部门自己的 Service 里最自然。
     */
    private final EmpMapper empMapper;

    @Override
    public List<Dept> list() {
        // 参数传 null 表示「不加任何查询条件」，等价于查全表。
        // 部门一共就几条，不做分页；员工、学员那种会越来越多的才需要 PageResult
        return deptMapper.selectList(null);
    }

    @Override
    public Dept getById(Long id) {
        Dept dept = deptMapper.selectById(id);
        // selectById 查不到时返回 null，不会抛异常，所以判空必须自己写
        if (dept == null) {
            throw new BusinessException("部门不存在");
        }
        return dept;
    }

    @Override
    public void save(DeptDTO deptDTO) {
        // 第二个参数传 null：新增时还没有 id，不存在「排除自己」的问题
        ensureNameNotTaken(deptDTO.getName(), null);

        // 只从 DTO 复制 name 过来，不像黑马课程那样用 BeanUtils 整体拷贝——
        // 字段少的时候一个一个 set 更清楚，也不会不小心把 createTime 带进来
        Dept dept = new Dept();
        dept.setName(deptDTO.getName());
        // createTime / updateTime 不赋值，留给数据库的 DEFAULT CURRENT_TIMESTAMP
        deptMapper.insert(dept);
    }

    @Override
    public void update(DeptDTO deptDTO) {
        // 先确认部门存在，再判重名。顺序不能反：
        // 如果先判重名，遇到「id 不存在、名字又刚好被占用」的请求会报「名称已存在」，
        // 但真正的问题是那个部门根本不存在，提示会把排查方向带偏
        if (deptMapper.selectById(deptDTO.getId()) == null) {
            throw new BusinessException("部门不存在");
        }
        ensureNameNotTaken(deptDTO.getName(), deptDTO.getId());

        Dept dept = new Dept();
        dept.setId(deptDTO.getId());
        dept.setName(deptDTO.getName());
        // updateById 只更新「非 null」的字段，实际落地的 SQL 是
        // UPDATE dept SET name = ? WHERE id = ?，update_time 由数据库自动刷新
        deptMapper.updateById(dept);
    }

    @Override
    public void deleteById(Long id) {
        // 这一段是 P3 的核心考点，值得多读两遍。
        //
        // emp.dept_id 上确实有外键 fk_emp_dept（RESTRICT），就算这里什么都不查，
        // 数据库也会拦住删除，数据绝不会坏。那为什么还要手动查一次？
        //
        // 因为「拦住」的方式不同：靠外键拦下来时抛的是 DataIntegrityViolationException，
        // 没人为它单独写处理器，会掉进全局异常的兜底分支，
        // 前端收到的是「系统繁忙，请稍后重试」——用户完全猜不到真实原因是
        // 「这个部门下面还有人」。手动查一次，才能给出能直接弹在界面上的话。
        //
        // 一句话：数据库约束保的是数据完整，业务校验保的是用户能看懂。
        Long empCount = empMapper.selectCount(
                new LambdaQueryWrapper<Emp>().eq(Emp::getDeptId, id));
        if (empCount > 0) {
            throw new BusinessException("该部门下还有员工，不能删除");
        }
        deptMapper.deleteById(id);
    }

    /**
     * 判重：名字被别的部门占了就抛业务异常。
     *
     * 数据库那一侧 dept.name 上有唯一键 uk_dept_name，重名数据本来就进不去。
     * 这里再查一遍不是冗余，理由和上面删部门那段一模一样：
     * 唯一键报错的原文是 `Duplicate entry '教研部' for key 'dept.uk_dept_name'`，
     * 那是给开发看的，不能直接甩给用户。
     * 唯一键是「最后一道防线」，Service 判重才是「主要手段」。
     *
     * @param excludeId 修改部门时传自己的 id，把自己排除在判重范围之外。
     *                  不排除的话，「名字原样提交一遍」这种什么都没改的请求
     *                  也会查到自己、被判成重名 —— 等于自己把自己挡住了。
     */
    private void ensureNameNotTaken(String name, Long excludeId) {
        LambdaQueryWrapper<Dept> wrapper = new LambdaQueryWrapper<Dept>()
                .eq(Dept::getName, name);
        if (excludeId != null) {
            wrapper.ne(Dept::getId, excludeId);
        }
        if (deptMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("部门名称已存在");
        }
    }

}
