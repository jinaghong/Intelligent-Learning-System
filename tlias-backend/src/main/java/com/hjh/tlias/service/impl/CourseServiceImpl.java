package com.hjh.tlias.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjh.tlias.common.exception.BusinessException;
import com.hjh.tlias.dto.CourseDTO;
import com.hjh.tlias.entity.Course;
import com.hjh.tlias.entity.StudentScore;
import com.hjh.tlias.mapper.CourseMapper;
import com.hjh.tlias.mapper.StudentScoreMapper;
import com.hjh.tlias.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程管理的业务实现。
 *
 * 和 DeptServiceImpl 一个模子：只做业务判断、只抛 BusinessException、
 * 每条业务规则都显式表达。
 */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;

    /**
     * 删课程前要数这门课有没有成绩，所以要用成绩的 Mapper。
     * 和「部门删之前数员工」一样，这是一条跨两张表的业务规则。
     */
    private final StudentScoreMapper studentScoreMapper;

    @Override
    public List<Course> list() {
        // 课程就几门，不分页。排序显式写出来——不写的话返回顺序是
        // 「碰巧」按主键，加了索引就可能变，列表的顺序应该是承诺而不是巧合
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .orderByAsc(Course::getId));
    }

    @Override
    public Course getById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        return course;
    }

    @Override
    public void save(CourseDTO courseDTO) {
        ensureNoNotTaken(courseDTO.getNo(), null);

        Course course = new Course();
        course.setName(courseDTO.getName());
        course.setNo(courseDTO.getNo());
        course.setPrice(courseDTO.getPrice());
        course.setDescription(courseDTO.getDescription());
        courseMapper.insert(course);
    }

    @Override
    public void update(CourseDTO courseDTO) {
        // 先确认课程存在，再判编号重复——顺序错了的话，
        // 「id 不存在、编号又刚好被占用」会报「编号已存在」，把排查方向带偏
        if (courseDTO.getId() == null) {
            throw new BusinessException("课程 id 不能为空");
        }
        if (courseMapper.selectById(courseDTO.getId()) == null) {
            throw new BusinessException("课程不存在");
        }
        ensureNoNotTaken(courseDTO.getNo(), courseDTO.getId());

        Course course = new Course();
        course.setId(courseDTO.getId());
        course.setName(courseDTO.getName());
        course.setNo(courseDTO.getNo());
        course.setPrice(courseDTO.getPrice());
        course.setDescription(courseDTO.getDescription());
        courseMapper.updateById(course);
    }

    @Override
    public void deleteById(Long id) {
        // P5a 的核心考点，和 P3 删部门是同一个思路的两步走：
        //
        // student_score.course_id 上有外键（RESTRICT），就算这里不查，
        // 数据库也会拦住删除。但靠外键拦下来抛的是 DataIntegrityViolationException，
        // 会掉进全局异常的兜底分支显示「系统繁忙」——用户猜不到真实原因是
        // 「这门课已经录入过成绩」。所以先手动数一遍，给出能看懂的话。
        //
        // 数据库约束保数据完整，业务校验保用户能看懂。
        Long scoreCount = studentScoreMapper.selectCount(
                new LambdaQueryWrapper<StudentScore>().eq(StudentScore::getCourseId, id));
        if (scoreCount > 0) {
            throw new BusinessException("该课程下已有成绩记录，不能删除");
        }
        courseMapper.deleteById(id);
    }

    /**
     * 判重：编号被别的课程占了就抛业务异常。
     *
     * 库里 uk_course_no 是最后一道防线（它的报错原文是给开发看的），
     * 这里才是主要手段。excludeId 供修改时排除自己——
     * 否则「编号原样提交一遍」会被自己挡住。
     */
    private void ensureNoNotTaken(String no, Long excludeId) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getNo, no);
        if (excludeId != null) {
            wrapper.ne(Course::getId, excludeId);
        }
        if (courseMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("课程编号已存在");
        }
    }

}
