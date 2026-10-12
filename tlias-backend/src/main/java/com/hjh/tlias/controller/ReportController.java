package com.hjh.tlias.controller;

import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.service.ReportService;
import com.hjh.tlias.vo.ClazzStudentCountVO;
import com.hjh.tlias.vo.ClazzViolationVO;
import com.hjh.tlias.vo.CourseScoreVO;
import com.hjh.tlias.vo.DeptEmpCountVO;
import com.hjh.tlias.vo.ScoreDistributionVO;
import com.hjh.tlias.vo.StudentDegreeVO;
import com.hjh.tlias.vo.StudentGenderVO;
import com.hjh.tlias.vo.ViolationTopVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据统计（P9 看板的数据源）。
 *
 * 全部只读、无参数、需登录（拦截器默认全拦，这里不开口子）。
 * 列表最多 10 行以内，不分页。
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
@Tag(name = "数据统计")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/clazz-student-count")
    @Operation(summary = "班级人数统计")
    public Result<List<ClazzStudentCountVO>> clazzStudentCount() {
        return Result.success(reportService.clazzStudentCount());
    }

    @GetMapping("/student-degree")
    @Operation(summary = "学员学历分布")
    public Result<List<StudentDegreeVO>> studentDegree() {
        return Result.success(reportService.studentDegree());
    }

    @GetMapping("/student-gender")
    @Operation(summary = "学员性别分布")
    public Result<List<StudentGenderVO>> studentGender() {
        return Result.success(reportService.studentGender());
    }

    @GetMapping("/course-score")
    @Operation(summary = "课程成绩统计")
    public Result<List<CourseScoreVO>> courseScore() {
        return Result.success(reportService.courseScore());
    }

    @GetMapping("/score-distribution")
    @Operation(summary = "成绩分数段分布")
    public Result<List<ScoreDistributionVO>> scoreDistribution() {
        return Result.success(reportService.scoreDistribution());
    }

    @GetMapping("/clazz-violation")
    @Operation(summary = "班级违纪统计")
    public Result<List<ClazzViolationVO>> clazzViolation() {
        return Result.success(reportService.clazzViolation());
    }

    @GetMapping("/violation-top")
    @Operation(summary = "违纪学员 Top10")
    public Result<List<ViolationTopVO>> violationTop() {
        return Result.success(reportService.violationTop());
    }

    @GetMapping("/dept-emp-count")
    @Operation(summary = "部门人数统计")
    public Result<List<DeptEmpCountVO>> deptEmpCount() {
        return Result.success(reportService.deptEmpCount());
    }

}
