package com.hjh.tlias.service;

import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.dto.LogQueryDTO;
import com.hjh.tlias.entity.OperLog;

/**
 * 操作日志。
 */
public interface LogService {

    /** 写入一条操作日志（由 LogAspect 调用） */
    void record(OperLog operLog);

    /** 分页查询，按操作时间倒序（最新的在最前） */
    PageResult<OperLog> page(LogQueryDTO query);

}
