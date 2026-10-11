package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjh.tlias.entity.StudentViolation;

/**
 * 学员违纪记录 Mapper。
 *
 * 只有最朴素的插入和按学员查询，BaseMapper 的方法够用，没有 XML。
 */
public interface StudentViolationMapper extends BaseMapper<StudentViolation> {
}
