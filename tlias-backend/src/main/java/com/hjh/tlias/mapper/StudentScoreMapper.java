package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjh.tlias.entity.StudentScore;

/**
 * 学员成绩 Mapper。
 *
 * P5a 只用它数「某门课有没有成绩」（课程删除守卫），
 * P6 数据统计阶段会真正用它做聚合查询。
 */
public interface StudentScoreMapper extends BaseMapper<StudentScore> {
}
