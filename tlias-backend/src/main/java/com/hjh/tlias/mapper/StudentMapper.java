package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hjh.tlias.dto.StudentQueryDTO;
import com.hjh.tlias.entity.Student;
import com.hjh.tlias.vo.StudentDetailVO;
import com.hjh.tlias.vo.StudentVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学员 Mapper。
 *
 * 两个自定义查询的 SQL 在 resources/mapper/StudentMapper.xml，都要带班级名，
 * 所以都要 LEFT JOIN clazz。数「一个班几个学员」用 BaseMapper 的 selectCount 就够了。
 */
public interface StudentMapper extends BaseMapper<Student> {

    /** 条件分页查询学员（带班级名） */
    IPage<StudentVO> selectPageWithClazz(IPage<StudentVO> page, @Param("q") StudentQueryDTO query);

    /** 条件查询全部学员（导出用，不分页） */
    List<StudentVO> selectListWithClazz(@Param("q") StudentQueryDTO query);

    /**
     * 按 id 查一个学员（带班级名）。
     * 返回的 StudentDetailVO 里 violationList 是空的——违纪明细由 Service 查了填。
     * 查不到返回 null，判空交给 Service。
     */
    StudentDetailVO selectDetailById(@Param("id") Long id);

}
