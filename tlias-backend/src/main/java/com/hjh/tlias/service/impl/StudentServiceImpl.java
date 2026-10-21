package com.hjh.tlias.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjh.tlias.common.exception.BusinessException;
import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.dto.StudentDTO;
import com.hjh.tlias.dto.StudentQueryDTO;
import com.hjh.tlias.dto.ViolationDTO;
import com.hjh.tlias.entity.Student;
import com.hjh.tlias.entity.StudentViolation;
import com.hjh.tlias.mapper.ClazzMapper;
import com.hjh.tlias.mapper.StudentMapper;
import com.hjh.tlias.mapper.StudentViolationMapper;
import com.hjh.tlias.service.StudentService;
import com.hjh.tlias.vo.StudentDetailVO;
import com.hjh.tlias.vo.StudentExportVO;
import com.hjh.tlias.vo.StudentVO;
import com.hjh.tlias.vo.ViolationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 学员管理的业务实现。
 *
 * 全项目的核心考点之一在 addViolation：违纪明细 + 两个汇总字段的「同事务累加」。
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;
    private final StudentViolationMapper studentViolationMapper;

    /** 校验 clazzId 指向真实班级要查 clazz 表 */
    private final ClazzMapper clazzMapper;

    @Override
    public PageResult<StudentVO> page(StudentQueryDTO query) {
        long current = query.getPage() == null ? 1 : query.getPage();
        long size = query.getPageSize() == null ? 10 : query.getPageSize();

        Page<StudentVO> page = new Page<>(current, size);
        IPage<StudentVO> result = studentMapper.selectPageWithClazz(page, query);
        return PageResult.from(result);
    }

    @Override
    public List<StudentExportVO> listForExport(StudentQueryDTO query) {
        return studentMapper.selectListWithClazz(query).stream()
                .map(this::toExportVO)
                .toList();
    }

    @Override
    public StudentDetailVO getDetailById(Long id) {
        StudentDetailVO detail = studentMapper.selectDetailById(id);
        if (detail == null) {
            throw new BusinessException("学员不存在");
        }
        // 违纪明细单独查一次：一对多 JOIN 会把学员行撑成多行，拆开查反而简单
        detail.setViolationList(listViolations(id));
        return detail;
    }

    @Override
    public void save(StudentDTO studentDTO) {
        ensureNoNotTaken(studentDTO.getNo(), null);
        ensureClazzExists(studentDTO.getClazzId());

        Student student = new Student();
        student.setName(studentDTO.getName());
        student.setNo(studentDTO.getNo());
        student.setGender(studentDTO.getGender());
        student.setPhone(studentDTO.getPhone());
        student.setDegree(studentDTO.getDegree());
        student.setClazzId(studentDTO.getClazzId());
        student.setIdCard(studentDTO.getIdCard());
        // isCollege 没传默认 0（非合作生源）。那列是 NOT NULL，留 null 会报「系统繁忙」
        student.setIsCollege(studentDTO.getIsCollege() == null ? 0 : studentDTO.getIsCollege());
        student.setAddress(studentDTO.getAddress());
        student.setGraduationDate(studentDTO.getGraduationDate());
        // violationCount / violationScore 不赋值：让数据库默认值（0）生效。
        // 它们是违纪处理累加出来的，新增表单里没有、也不该有这两个字段
        studentMapper.insert(student);
    }

    @Override
    public void update(StudentDTO studentDTO) {
        if (studentDTO.getId() == null) {
            throw new BusinessException("学员 id 不能为空");
        }
        if (studentMapper.selectById(studentDTO.getId()) == null) {
            throw new BusinessException("学员不存在");
        }
        ensureNoNotTaken(studentDTO.getNo(), studentDTO.getId());
        ensureClazzExists(studentDTO.getClazzId());

        Student student = new Student();
        student.setId(studentDTO.getId());
        student.setName(studentDTO.getName());
        student.setNo(studentDTO.getNo());
        student.setGender(studentDTO.getGender());
        student.setPhone(studentDTO.getPhone());
        student.setDegree(studentDTO.getDegree());
        student.setClazzId(studentDTO.getClazzId());
        student.setIdCard(studentDTO.getIdCard());
        student.setIsCollege(studentDTO.getIsCollege());
        student.setAddress(studentDTO.getAddress());
        student.setGraduationDate(studentDTO.getGraduationDate());
        // 注意：这里没 set violationCount / violationScore——
        // updateById 只更新非 null 字段，不 set 它们就不会出现在 UPDATE 语句里，
        // 汇总数字不会被「改资料」顺手覆盖掉
        studentMapper.updateById(student);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的学员");
        }
        // 不需要事务：只有一条 DELETE。student_violation 上是 ON DELETE CASCADE，
        // 违纪明细由数据库跟着删
        studentMapper.deleteByIds(ids);
    }

    /**
     * 违纪处理 —— 计划书点名的核心考点。
     *
     * 三件事必须一起发生：
     *   1. student_violation 插一条明细（事实）
     *   2. student.violation_count 加 1（汇总）
     *   3. student.violation_score 加上本次扣分（汇总）
     * 任何一步单独发生都是脏数据，所以 @Transactional 不能省。
     *
     * 汇总字段为什么不用「先 selectById 读出来、在 Java 里加、再 updateById」？
     * 那是典型的「读-改-写」，并发下会丢更新：两笔违纪同时提交，
     * 双方都读到 0、都写回 1，结果记了两次只涨了 1。
     * 把加法交给数据库（violation_count = violation_count + 1）才是原子的——
     * SQL 里的「自增」由行锁保证，并发多少笔都不会丢。
     */
    @Override
    @Transactional
    public void addViolation(ViolationDTO violationDTO) {
        if (studentMapper.selectById(violationDTO.getStudentId()) == null) {
            throw new BusinessException("学员不存在");
        }

        StudentViolation violation = new StudentViolation();
        violation.setStudentId(violationDTO.getStudentId());
        violation.setPoint(violationDTO.getPoint());
        violation.setDescription(violationDTO.getDescription());
        studentViolationMapper.insert(violation);

        // setSql 直接写「字段 = 字段 + N」，由数据库执行原子加法。
        // point 是 Integer 类型（反序列化保证是数字），拼接进 SQL 没有注入风险
        studentMapper.update(null, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, violationDTO.getStudentId())
                .setSql("violation_count = violation_count + 1")
                .setSql("violation_score = violation_score + " + violationDTO.getPoint()));
    }

    /** 列表 VO -> 导出 VO，把库里的编码翻成给人看的中文 */
    private StudentExportVO toExportVO(StudentVO student) {
        StudentExportVO vo = new StudentExportVO();
        vo.setName(student.getName());
        vo.setNo(student.getNo());
        vo.setGender(genderText(student.getGender()));
        vo.setPhone(student.getPhone() == null ? "" : student.getPhone());
        vo.setDegree(degreeText(student.getDegree()));
        vo.setClazzName(student.getClazzName() == null ? "" : student.getClazzName());
        vo.setIsCollege(isCollegeText(student.getIsCollege()));
        vo.setViolationCount(student.getViolationCount() == null ? 0 : student.getViolationCount());
        vo.setViolationScore(student.getViolationScore() == null ? 0 : student.getViolationScore());
        return vo;
    }

    private String genderText(Integer code) {
        if (code == null) {
            return "";
        }
        return code == 1 ? "男" : "女";
    }

    private String degreeText(Integer code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case 1 -> "初中";
            case 2 -> "高中";
            case 3 -> "专科";
            case 4 -> "本科";
            case 5 -> "硕士";
            case 6 -> "博士";
            default -> String.valueOf(code);
        };
    }

    private String isCollegeText(Integer code) {
        if (code == null) {
            return "";
        }
        return code == 1 ? "是" : "否";
    }

    /** 违纪明细，新的在前 */
    private List<ViolationVO> listViolations(Long studentId) {
        return studentViolationMapper.selectList(new LambdaQueryWrapper<StudentViolation>()
                        .eq(StudentViolation::getStudentId, studentId)
                        .orderByDesc(StudentViolation::getId))
                .stream()
                .map(v -> {
                    ViolationVO vo = new ViolationVO();
                    vo.setId(v.getId());
                    vo.setStudentId(v.getStudentId());
                    vo.setPoint(v.getPoint());
                    vo.setDescription(v.getDescription());
                    vo.setCreateTime(v.getCreateTime());
                    return vo;
                })
                .toList();
    }

    /**
     * 判重：学号被别的学员占了就抛业务异常（库里 uk_student_no 是最后一道防线）。
     */
    private void ensureNoNotTaken(String no, Long excludeId) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<Student>()
                .eq(Student::getNo, no);
        if (excludeId != null) {
            wrapper.ne(Student::getId, excludeId);
        }
        if (studentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("学号已存在");
        }
    }

    /** 班级可以不选（没分班），但选了就必须是真实存在的班级 */
    private void ensureClazzExists(Long clazzId) {
        if (clazzId != null && clazzMapper.selectById(clazzId) == null) {
            throw new BusinessException("班级不存在");
        }
    }

}
