package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjh.tlias.entity.Dept;

/**
 * 部门 Mapper。
 *
 * 和 EmpMapper 一样，继承 BaseMapper 就够了 ——
 * selectList / selectById / selectCount / insert / updateById / deleteById
 * 全部由 MyBatis-Plus 现成提供，这个接口一行方法都不用声明。
 *
 * 不需要 @Mapper 注解：主启动类上的 @MapperScan("com.hjh.tlias.mapper")
 * 会扫这个包下所有接口并生成实现类。
 */
public interface DeptMapper extends BaseMapper<Dept> {

}
