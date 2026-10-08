package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjh.tlias.entity.Emp;

/**
 * 员工 Mapper。
 *
 * 继承 BaseMapper 之后，增删改查这些最常规的操作就不用自己写了——
 * selectById / selectList / insert / updateById / deleteById 全是现成的。
 * 只有 MyBatis-Plus 猜不出来的复杂查询（比如带多表关联的分页）才需要自己写方法。
 *
 * 这个接口不需要 @Mapper 注解，因为主启动类上已经有 @MapperScan("com.hjh.tlias.mapper")，
 * 会扫描这个包下所有接口并自动生成实现类。
 */
public interface EmpMapper extends BaseMapper<Emp> {

}
