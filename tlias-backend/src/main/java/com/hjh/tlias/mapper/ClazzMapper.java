package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hjh.tlias.dto.ClazzQueryDTO;
import com.hjh.tlias.entity.Clazz;
import com.hjh.tlias.vo.ClazzVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 班级 Mapper。
 *
 * 三个自定义查询的 SQL 都在 resources/mapper/ClazzMapper.xml，
 * 靠「方法名 = <select> 的 id」对上号。都需要带班主任姓名，所以都要 LEFT JOIN emp。
 */
public interface ClazzMapper extends BaseMapper<Clazz> {

    /** 条件分页查询班级（带班主任姓名）。分页由 IPage 参数触发分页插件接管 */
    IPage<ClazzVO> selectPageWithMaster(IPage<ClazzVO> page, @Param("q") ClazzQueryDTO query);

    /** 查全部班级（带班主任姓名），给下拉框用——不分页 */
    List<ClazzVO> selectAllWithMaster();

    /** 按 id 查一个班级（带班主任姓名），查不到返回 null */
    ClazzVO selectDetailById(@Param("id") Long id);

}
