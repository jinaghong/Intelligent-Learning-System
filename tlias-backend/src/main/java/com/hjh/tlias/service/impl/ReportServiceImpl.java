package com.hjh.tlias.service.impl;

import com.hjh.tlias.mapper.ReportMapper;
import com.hjh.tlias.service.ReportService;
import com.hjh.tlias.vo.ClazzStudentCountVO;
import com.hjh.tlias.vo.ClazzViolationVO;
import com.hjh.tlias.vo.CourseScoreVO;
import com.hjh.tlias.vo.DeptEmpCountVO;
import com.hjh.tlias.vo.ScoreDistributionVO;
import com.hjh.tlias.vo.StudentDegreeVO;
import com.hjh.tlias.vo.StudentGenderVO;
import com.hjh.tlias.vo.ViolationTopVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计实现。
 *
 * 分工：Mapper 只负责「编码 + 数量」的纯聚合；
 * 中文标签与「缺失类目补 0」在这里做——保证封闭枚举（学历 6 类 / 性别 2 类）
 * 行数恒定，P9 的图坐标轴不随数据抖动。
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    /** 下标 = 学历编码；0 号位空占位，让 degree 直接当下标用 */
    private static final String[] DEGREE_NAMES = {"", "初中", "高中", "专科", "本科", "硕士", "博士"};

    /** 分数段固定 5 档：SQL 聚合结果缺哪档，Service 补 0 保证行数恒定 */
    private static final List<String> SCORE_RANGES = List.of("0-59", "60-69", "70-79", "80-89", "90-100");

    private final ReportMapper reportMapper;

    @Override
    public List<ClazzStudentCountVO> clazzStudentCount() {
        return reportMapper.clazzStudentCount();
    }

    @Override
    public List<StudentDegreeVO> studentDegree() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (StudentDegreeVO row : reportMapper.studentDegree()) {
            counts.put(row.getDegree(), row.getCount());
        }

        List<StudentDegreeVO> result = new ArrayList<>();
        for (int degree = 1; degree <= 6; degree++) {
            StudentDegreeVO vo = new StudentDegreeVO();
            vo.setDegree(degree);
            vo.setDegreeName(DEGREE_NAMES[degree]);
            vo.setCount(counts.getOrDefault(degree, 0));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<StudentGenderVO> studentGender() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (StudentGenderVO row : reportMapper.studentGender()) {
            counts.put(row.getGender(), row.getCount());
        }

        List<StudentGenderVO> result = new ArrayList<>();
        for (int gender = 1; gender <= 2; gender++) {
            StudentGenderVO vo = new StudentGenderVO();
            vo.setGender(gender);
            vo.setGenderName(gender == 1 ? "男" : "女");
            vo.setCount(counts.getOrDefault(gender, 0));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<CourseScoreVO> courseScore() {
        return reportMapper.courseScore();
    }

    @Override
    public List<ScoreDistributionVO> scoreDistribution() {
        Map<String, Integer> counts = new HashMap<>();
        for (ScoreDistributionVO row : reportMapper.scoreDistribution()) {
            counts.put(row.getRange(), row.getCount());
        }

        List<ScoreDistributionVO> result = new ArrayList<>();
        for (String range : SCORE_RANGES) {
            ScoreDistributionVO vo = new ScoreDistributionVO();
            vo.setRange(range);
            vo.setCount(counts.getOrDefault(range, 0));
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<ClazzViolationVO> clazzViolation() {
        return reportMapper.clazzViolation();
    }

    @Override
    public List<ViolationTopVO> violationTop() {
        return reportMapper.violationTop();
    }

    @Override
    public List<DeptEmpCountVO> deptEmpCount() {
        return reportMapper.deptEmpCount();
    }

}
