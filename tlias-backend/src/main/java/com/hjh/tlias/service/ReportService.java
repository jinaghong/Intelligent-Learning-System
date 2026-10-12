package com.hjh.tlias.service;

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
 * 数据统计服务（P9 看板的数据源）。
 */
public interface ReportService {

    List<ClazzStudentCountVO> clazzStudentCount();

    List<StudentDegreeVO> studentDegree();

    List<StudentGenderVO> studentGender();

    List<CourseScoreVO> courseScore();

    List<ScoreDistributionVO> scoreDistribution();

    List<ClazzViolationVO> clazzViolation();

    List<ViolationTopVO> violationTop();

    List<DeptEmpCountVO> deptEmpCount();

}
