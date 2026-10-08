package com.hjh.tlias.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页查询的统一响应体。
 *
 * 和 Result 的关系：Result 负责「这次请求成没成功」，
 * PageResult 负责「成功的这次返回了哪一页数据」，两者嵌套使用：
 *   Result<PageResult<Emp>>  —— 成功 + 这一页 10 条 + 总共 156 条
 *
 * total: 满足条件的总记录数（不是这一页的条数）
 * rows:  当前这一页的数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private Long total;

    private List<T> rows;

    public static <T> PageResult<T> of(Long total, List<T> rows) {
        return new PageResult<>(total, rows);
    }

    /** 直接吃 MyBatis-Plus 分页插件返回的 IPage，省掉手工取 total/records */
    public static <T> PageResult<T> from(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

}
