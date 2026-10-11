package com.hjh.tlias.service;

import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.dto.ClazzDTO;
import com.hjh.tlias.dto.ClazzQueryDTO;
import com.hjh.tlias.vo.ClazzVO;

import java.util.List;

public interface ClazzService {

    PageResult<ClazzVO> page(ClazzQueryDTO query);

    /** 全部班级（带班主任姓名），给学员表单的下拉框用 */
    List<ClazzVO> listAll();

    ClazzVO getById(Long id);

    void save(ClazzDTO clazzDTO);

    void update(ClazzDTO clazzDTO);

    void deleteById(Long id);

}
