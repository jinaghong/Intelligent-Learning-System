package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hjh.tlias.dto.EmpQueryDTO;
import com.hjh.tlias.entity.Emp;
import com.hjh.tlias.vo.EmpDetailVO;
import com.hjh.tlias.vo.EmpVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 员工 Mapper。
 *
 * 增删改查这些常规操作继承 BaseMapper 就够了。只有「MyBatis-Plus 猜不出来」的查询
 * 才需要自己声明方法——条件分页 + 多表关联就是典型的一种。
 *
 * @Mapper 注解不用写，主启动类上的 @MapperScan 会扫这个包。
 */
public interface EmpMapper extends BaseMapper<Emp> {

    /**
     * 条件分页查询员工，并带上部门名称。
     *
     * 这个方法的 SQL 没写在注解里，写在 resources/mapper/EmpMapper.xml —— 靠
     * 「方法名 = XML 里 <select> 的 id」对上号，namespace 必须是本接口的全限定名。
     *
     * 分页不用自己写 LIMIT：第一个参数是 IPage，MyBatis-Plus 的
     * PaginationInnerInterceptor 看到它就会自动接管——
     * 给 SQL 套上 LIMIT，并另外跑一条 COUNT 拿总数，两个结果都塞回这个 IPage。
     *
     * ⚠️ 这条自动接管正是 pom.xml 里必须单独引 mybatis-plus-jsqlparser 的原因：
     *    插件要靠 jsqlparser 解析你手写的 SQL 才能改写它。漏引会报
     *    NoClassDefFoundError: PaginationInnerInterceptor。
     *
     * @param page  分页参数（页码、每页条数），会被插件读写
     * @param query 筛选条件，@Param("q") 让它在 XML 里以 #{q.name} 的形式访问
     */
    IPage<EmpVO> selectPageWithDept(IPage<EmpVO> page, @Param("q") EmpQueryDTO query);

    /**
     * 按同样的条件查全部员工（不分页），给 Excel 导出用。
     *
     * 导出和列表用的是同一套 Where 条件（XML 里抽成了 queryConditions 片段），
     * 但导出不能分页——分页导出只会导出当前这一页，用户在页面上看不到
     * 却以为导全了。所以这里单独开一个返回 List 的方法。
     */
    List<EmpVO> selectListWithDept(@Param("q") EmpQueryDTO query);

    /**
     * 按 id 查一个员工，带部门名。
     *
     * 返回的 EmpDetailVO 里 exprList 是空的——工作经历在另一张表，
     * 由 Service 再查一次填进去。为什么不在这里一次 JOIN 出来？
     * 因为一个员工有多条经历，JOIN 之后会变成多行，还得在 Java 里
     * 手工把重复的员工信息合并去重，比查两次麻烦得多。
     *
     * 查不到时返回 null（MyBatis 的单条查询查不到不抛异常），判空交给 Service。
     */
    EmpDetailVO selectDetailById(@Param("id") Long id);

}
