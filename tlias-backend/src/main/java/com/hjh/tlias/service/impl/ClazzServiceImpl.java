package com.hjh.tlias.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjh.tlias.common.exception.BusinessException;
import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.dto.ClazzDTO;
import com.hjh.tlias.dto.ClazzQueryDTO;
import com.hjh.tlias.entity.Clazz;
import com.hjh.tlias.entity.Student;
import com.hjh.tlias.mapper.ClazzMapper;
import com.hjh.tlias.mapper.EmpMapper;
import com.hjh.tlias.mapper.StudentMapper;
import com.hjh.tlias.service.ClazzService;
import com.hjh.tlias.vo.ClazzVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 班级管理的业务实现。
 *
 * 比部门 / 课程多出来的两条业务规则：
 *   1. 班主任可以不选，选了必须真实存在（跨 emp 表的校验）
 *   2. 结班日期不能早于开班日期（同一条记录内的字段关系校验）
 * 删除守卫（班里有学员不许删）和 P3 / P5a 是同一个思路。
 */
@Service
@RequiredArgsConstructor
public class ClazzServiceImpl implements ClazzService {

    private final ClazzMapper clazzMapper;

    /** 校验 masterId 指向真实员工要查 emp 表 */
    private final EmpMapper empMapper;

    /** 删班级前要数班里有多少学员 */
    private final StudentMapper studentMapper;

    @Override
    public PageResult<ClazzVO> page(ClazzQueryDTO query) {
        // 和 EmpServiceImpl.page 同样的兜底：?page= 这种显式空值会把默认值覆盖成 null
        long current = query.getPage() == null ? 1 : query.getPage();
        long size = query.getPageSize() == null ? 10 : query.getPageSize();

        Page<ClazzVO> page = new Page<>(current, size);
        IPage<ClazzVO> result = clazzMapper.selectPageWithMaster(page, query);
        return PageResult.from(result);
    }

    @Override
    public List<ClazzVO> listAll() {
        return clazzMapper.selectAllWithMaster();
    }

    @Override
    public ClazzVO getById(Long id) {
        ClazzVO clazz = clazzMapper.selectDetailById(id);
        if (clazz == null) {
            throw new BusinessException("班级不存在");
        }
        return clazz;
    }

    @Override
    public void save(ClazzDTO clazzDTO) {
        ensureNameNotTaken(clazzDTO.getName(), null);
        ensureMasterExists(clazzDTO.getMasterId());
        ensureDateOrder(clazzDTO);

        Clazz clazz = new Clazz();
        clazz.setName(clazzDTO.getName());
        clazz.setRoom(clazzDTO.getRoom());
        clazz.setBeginDate(clazzDTO.getBeginDate());
        clazz.setEndDate(clazzDTO.getEndDate());
        clazz.setMasterId(clazzDTO.getMasterId());
        clazz.setSubject(clazzDTO.getSubject());
        // status 没传默认在读（1）。那一列是 NOT NULL，不给默认值会报「系统繁忙」
        clazz.setStatus(clazzDTO.getStatus() == null ? 1 : clazzDTO.getStatus());
        clazzMapper.insert(clazz);
    }

    @Override
    public void update(ClazzDTO clazzDTO) {
        if (clazzDTO.getId() == null) {
            throw new BusinessException("班级 id 不能为空");
        }
        if (clazzMapper.selectById(clazzDTO.getId()) == null) {
            throw new BusinessException("班级不存在");
        }
        ensureNameNotTaken(clazzDTO.getName(), clazzDTO.getId());
        ensureMasterExists(clazzDTO.getMasterId());
        ensureDateOrder(clazzDTO);

        Clazz clazz = new Clazz();
        clazz.setId(clazzDTO.getId());
        clazz.setName(clazzDTO.getName());
        clazz.setRoom(clazzDTO.getRoom());
        clazz.setBeginDate(clazzDTO.getBeginDate());
        clazz.setEndDate(clazzDTO.getEndDate());
        clazz.setMasterId(clazzDTO.getMasterId());
        clazz.setSubject(clazzDTO.getSubject());
        // status 不传（null）时 updateById 不会 set 这一列，保持原状态——
        // 「未传 = 不动」和 P4 里密码的语义保持一致
        clazz.setStatus(clazzDTO.getStatus());
        clazzMapper.updateById(clazz);
    }

    @Override
    public void deleteById(Long id) {
        // 和 P3 删部门、P5a 删课程完全同构：student.clazz_id 上是 RESTRICT 外键，
        // 靠它拦下来用户只会看到「系统繁忙」；先手动数一遍才能给出人话
        Long studentCount = studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().eq(Student::getClazzId, id));
        if (studentCount > 0) {
            throw new BusinessException("该班级下还有学员，不能删除");
        }
        clazzMapper.deleteById(id);
    }

    /**
     * 判重：班级名被别的班占了就抛业务异常（库里 uk_clazz_name 是最后一道防线）。
     * excludeId 供修改时排除自己。
     */
    private void ensureNameNotTaken(String name, Long excludeId) {
        LambdaQueryWrapper<Clazz> wrapper = new LambdaQueryWrapper<Clazz>()
                .eq(Clazz::getName, name);
        if (excludeId != null) {
            wrapper.ne(Clazz::getId, excludeId);
        }
        if (clazzMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("班级名称已存在");
        }
    }

    /**
     * 班主任可以不选（master_id 允许 null），但选了就必须是真实存在的员工。
     * 不查的话坏 masterId 会撞外键报 DataIntegrityViolationException，
     * 掉进兜底分支显示「系统繁忙」。
     */
    private void ensureMasterExists(Long masterId) {
        if (masterId != null && empMapper.selectById(masterId) == null) {
            throw new BusinessException("班主任不存在");
        }
    }

    /**
     * 结班日期不能早于开班日期。
     *
     * 这条规则也可以写成 DTO 上的自定义校验注解，但「两个字段之间的关系」
     * 用注解表达要绕一圈（写类级 @AssertTrue 方法）；写在 Service 里
     * 一句话说清，还不用教读者新的注解。规则简单时，简单写法就是好写法。
     */
    private void ensureDateOrder(ClazzDTO clazzDTO) {
        if (clazzDTO.getBeginDate().isAfter(clazzDTO.getEndDate())) {
            throw new BusinessException("结班日期不能早于开班日期");
        }
    }

}
