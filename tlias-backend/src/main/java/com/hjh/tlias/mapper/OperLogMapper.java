package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjh.tlias.entity.OperLog;

/**
 * 操作日志 Mapper。继承 BaseMapper，insert / selectPage 全部现成。
 *
 * 日志表只有「插」和「查」，永远没有 update / delete——
 * 审计日志只增不改是基本原则，所以这里也确实用不到那两个方法。
 */
public interface OperLogMapper extends BaseMapper<OperLog> {

}
