package com.hjh.tlias.controller;

import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.dto.DeptDTO;
import com.hjh.tlias.entity.Dept;
import com.hjh.tlias.service.DeptService;
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
 * 部门管理。
 *
 * 这个类里没有一个 try/catch —— 业务异常由 Service 抛出来，
 * 全局异常处理器接住并转成统一的 Result，Controller 只负责「收单子、递结果」。
 *
 * 全部 5 个接口都在登录拦截器的保护范围内（WebMvcConfig 里没排除 /depts），
 * 不带 tlias-token 请求一律 401。
 */
@RestController
@RequestMapping("/depts")
@RequiredArgsConstructor
@Tag(name = "部门管理")
public class DeptController {

    private final DeptService deptService;

    @GetMapping
    @Operation(summary = "查询全部部门")
    public Result<List<Dept>> list() {
        return Result.success(deptService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 id 查询部门")
    // @PathVariable 里的 "id" 要和路径上的 {id} 写成一样的名字。
    // 本项目的编译参数保留了方法参数名，理论上写 @PathVariable Long id 也能找到，
    // 但显式写全更稳，换个构建配置也不会出问题
    public Result<Dept> getById(@PathVariable("id") Long id) {
        return Result.success(deptService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增部门")
    // @Valid 必须写：DTO 上的 @NotBlank 只是「声明了规则」，
    // 加上 @Valid 才是「真正去执行校验」。漏了它，空名字会一路走到数据库
    public Result<Void> save(@Valid @RequestBody DeptDTO deptDTO) {
        deptService.save(deptDTO);
        // 新增成功的响应里不需要带回数据，前端拿到 code=1 就够了，
        // 列表会重新拉一次。所以用不带参数的 success()
        return Result.success();
    }

    @PutMapping
    @Operation(summary = "修改部门")
    public Result<Void> update(@Valid @RequestBody DeptDTO deptDTO) {
        deptService.update(deptDTO);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除部门")
    public Result<Void> delete(@PathVariable("id") Long id) {
        deptService.deleteById(id);
        return Result.success();
    }

}
