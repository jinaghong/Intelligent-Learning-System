package com.hjh.tlias.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hjh.tlias.entity.AiCallLog;

/**
 * AI 调用日志 Mapper。
 *
 * 只插不查不改不删（审计日志的基本原则，和 OperLogMapper 一样）。
 * 验收/排查要看数据，直接连库查——不做查询接口是 YAGNI：
 * P9 前端真有「AI 调用记录」页面需求时再加。
 */
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
}
