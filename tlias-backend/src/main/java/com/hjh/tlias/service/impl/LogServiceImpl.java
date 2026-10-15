package com.hjh.tlias.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.dto.LogQueryDTO;
import com.hjh.tlias.entity.OperLog;
import com.hjh.tlias.mapper.OperLogMapper;
import com.hjh.tlias.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final OperLogMapper operLogMapper;

    @Override
    public void record(OperLog operLog) {
        operLogMapper.insert(operLog);
    }

    @Override
    public PageResult<OperLog> page(LogQueryDTO query) {
        // 「?page=」（显式空值）会被绑定成 null，@Min 校验拦不住 null（null 表示「没传」），
        // 这里兜底成默认页码。P4 在员工分页上踩过同一个坑
        int page = query.getPage() == null ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();

        Page<OperLog> mpPage = new Page<>(page, pageSize);
        // 双键排序：同一毫秒内的多条日志靠 id 分出先后，顺序才稳定（P4 排序教训）
        LambdaQueryWrapper<OperLog> wrapper = new LambdaQueryWrapper<OperLog>()
                .orderByDesc(OperLog::getOperateTime)
                .orderByDesc(OperLog::getId);
        return PageResult.from(operLogMapper.selectPage(mpPage, wrapper));
    }

}
