package com.hjh.tlias.service;

import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.dto.EmpDTO;
import com.hjh.tlias.dto.EmpQueryDTO;
import com.hjh.tlias.vo.EmpDetailVO;
import com.hjh.tlias.vo.EmpExportVO;
import com.hjh.tlias.vo.EmpVO;

import java.util.List;

public interface EmpService {

    /** 按条件分页查询员工，每行带部门名称 */
    PageResult<EmpVO> page(EmpQueryDTO query);

    /** 按 id 查一个员工，带上他的工作经历 */
    EmpDetailVO getDetailById(Long id);

    /** 新增员工，连同工作经历一起保存 */
    void save(EmpDTO empDTO);

    /** 修改员工，工作经历整体替换 */
    void update(EmpDTO empDTO);

    /** 批量删除员工 */
    void deleteByIds(List<Long> ids);

    /**
     * 按同样的筛选条件查出全部员工，转成导出用的形状（中文、不分页）。
     *
     * 返回的是数据而不是「写好的文件」，因为拼 Excel、设置下载响应头
     * 属于 HTTP 那一层的事，Service 不该知道 HttpServletResponse 是什么。
     */
    List<EmpExportVO> listForExport(EmpQueryDTO query);

}
