package com.hjh.tlias.controller;

import com.hjh.tlias.annotation.Log;
import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.dto.ClazzDTO;
import com.hjh.tlias.dto.ClazzQueryDTO;
import com.hjh.tlias.service.ClazzService;
import com.hjh.tlias.vo.ClazzVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 班级管理。
 *
 * 注意 /clazzs/list 和 /clazzs/{id} 长得像，其实不会打架：
 * Spring 匹配时「写死的路径段」优先于「{占位符}」，
 * 所以 /clazzs/list 一定走 listAll，不会被当成 id="list" 去查详情。
 */
@RestController
@RequestMapping("/clazzs")
@RequiredArgsConstructor
@Tag(name = "班级管理")
public class ClazzController {

    private final ClazzService clazzService;

    /** 条件分页查询（带班主任姓名）。@Valid 触发 ClazzQueryDTO 的分页下限校验 */
    @GetMapping
    @Operation(summary = "条件分页查询班级")
    public Result<PageResult<ClazzVO>> page(@Valid ClazzQueryDTO query) {
        return Result.success(clazzService.page(query));
    }

    @GetMapping("/list")
    @Operation(summary = "查询全部班级（下拉框用）")
    public Result<List<ClazzVO>> listAll() {
        return Result.success(clazzService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 id 查询班级")
    public Result<ClazzVO> getById(@PathVariable("id") Long id) {
        return Result.success(clazzService.getById(id));
    }

    @Log
    @PostMapping
    @Operation(summary = "新增班级")
    public Result<Void> save(@Valid @RequestBody ClazzDTO clazzDTO) {
        clazzService.save(clazzDTO);
        return Result.success();
    }

    @Log
    @PutMapping
    @Operation(summary = "修改班级")
    public Result<Void> update(@Valid @RequestBody ClazzDTO clazzDTO) {
        clazzService.update(clazzDTO);
        return Result.success();
    }

    @Log
    @DeleteMapping("/{id}")
    @Operation(summary = "删除班级")
    public Result<Void> delete(@PathVariable("id") Long id) {
        clazzService.deleteById(id);
        return Result.success();
    }

}
