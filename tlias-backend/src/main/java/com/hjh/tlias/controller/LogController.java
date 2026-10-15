package com.hjh.tlias.controller;

import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.dto.LogQueryDTO;
import com.hjh.tlias.entity.OperLog;
import com.hjh.tlias.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志查询。
 *
 * 注意这个接口自己不贴 @Log：它是只读查询，日志只记「写操作」，
 * 查询太频繁，记了会把有价值的写操作日志淹没掉。
 *
 * /logs 不在 WebMvcConfig 的放行名单里，所以默认就需要登录——
 * 审计数据当然不能匿名看。
 */
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
@Tag(name = "操作日志")
public class LogController {

    private final LogService logService;

    @GetMapping
    @Operation(summary = "操作日志分页查询（时间倒序）")
    public Result<PageResult<OperLog>> page(@Valid LogQueryDTO query) {
        return Result.success(logService.page(query));
    }

}
