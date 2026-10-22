package com.hjh.tlias.service;

import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.dto.StudentDTO;
import com.hjh.tlias.dto.StudentQueryDTO;
import com.hjh.tlias.dto.ViolationDTO;
import com.hjh.tlias.vo.StudentDetailVO;
import com.hjh.tlias.vo.StudentExportVO;
import com.hjh.tlias.vo.StudentVO;

import java.util.List;

public interface StudentService {

    PageResult<StudentVO> page(StudentQueryDTO query);

    StudentDetailVO getDetailById(Long id);

    void save(StudentDTO studentDTO);

    void update(StudentDTO studentDTO);

    void deleteByIds(List<Long> ids);

    /** 导出用：按条件查全部学员，编码翻译成中文 */
    List<StudentExportVO> listForExport(StudentQueryDTO query);

    /** 违纪处理：记一条明细 + 同事务累加两个汇总字段 */
    void addViolation(ViolationDTO violationDTO);

}
