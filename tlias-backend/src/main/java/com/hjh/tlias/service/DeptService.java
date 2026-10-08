package com.hjh.tlias.service;

import com.hjh.tlias.dto.DeptDTO;
import com.hjh.tlias.entity.Dept;

import java.util.List;

/**
 * 部门管理的业务接口。
 *
 * 接口里不出现「数据库」三个字，也不出现 Wrapper、Mapper 这些词 ——
 * Controller 只知道「有这么几件事可以办」，不知道底下怎么存的。
 * 将来要把部门改成从缓存读，只需要换实现类，Controller 一行都不用动。
 */
public interface DeptService {

    /** 查询全部部门 */
    List<Dept> list();

    /** 根据 id 查一个部门，查不到抛业务异常 */
    Dept getById(Long id);

    /** 新增部门，重名则抛业务异常 */
    void save(DeptDTO deptDTO);

    /** 修改部门，部门不存在或重名则抛业务异常 */
    void update(DeptDTO deptDTO);

    /** 删除部门，部门下还有员工则抛业务异常 */
    void deleteById(Long id);

}
