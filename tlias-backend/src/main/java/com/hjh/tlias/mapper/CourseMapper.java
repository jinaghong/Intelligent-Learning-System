package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjh.tlias.entity.Course;

/**
 * 课程 Mapper。
 *
 * 课程没有多表查询、没有条件筛选（一共就几门课，列表全查），
 * 所以一个 BaseMapper 提供的方法就够用，不需要声明任何自定义方法，
 * 也就没有对应的 XML 文件。
 *
 * @Mapper 注解不用写，主启动类上的 @MapperScan 会扫这个包。
 */
public interface CourseMapper extends BaseMapper<Course> {
}
