package com.hjh.tlias.mapper;

import com.hjh.tlias.vo.ClazzStudentCountVO;
import com.hjh.tlias.vo.ClazzViolationVO;
import com.hjh.tlias.vo.CourseScoreVO;
import com.hjh.tlias.vo.DeptEmpCountVO;
import com.hjh.tlias.vo.ScoreDistributionVO;
import com.hjh.tlias.vo.StudentDegreeVO;
import com.hjh.tlias.vo.StudentGenderVO;
import com.hjh.tlias.vo.ViolationTopVO;

import java.util.List;

/**
 * 数据统计 Mapper。
 *
 * 不继承 BaseMapper：统计查询没有对应的「单表实体」，
 * 每个方法都是一条聚合 SQL，全部写在 ReportMapper.xml（P4 惯例：复杂 SQL 走 XML）。
 *
 * 不需要 @Mapper 注解：主启动类上的 @MapperScan("com.hjh.tlias.mapper") 统一扫描。
 */
public interface ReportMapper {

    List<ClazzStudentCountVO> clazzStudentCount();

    List<StudentDegreeVO> studentDegree();

    List<StudentGenderVO> studentGender();

    List<CourseScoreVO> courseScore();

    List<ScoreDistributionVO> scoreDistribution();

    List<ClazzViolationVO> clazzViolation();

    List<ViolationTopVO> violationTop();

    List<DeptEmpCountVO> deptEmpCount();

}
