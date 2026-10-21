package com.hjh.tlias.controller;

import com.hjh.tlias.annotation.Log;
import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.dto.EmpDTO;
import com.hjh.tlias.dto.EmpQueryDTO;
import com.hjh.tlias.service.DeptService;
import com.hjh.tlias.service.EmpService;
import com.hjh.tlias.util.ExcelUtil;
import com.hjh.tlias.vo.EmpDetailVO;
import com.hjh.tlias.vo.EmpExportVO;
import com.hjh.tlias.vo.EmpVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/emps")
@RequiredArgsConstructor
@Tag(name = "员工管理")
public class EmpController {

    private final EmpService empService;

    /** 按部门导出时用部门名拼文件名 */
    private final DeptService deptService;

    /**
     * 条件分页查询员工。
     *
     * 注意参数上没有 @RequestBody —— 这是 GET，条件全在 URL 的查询串里
     * （?name=李&gender=1&page=2）。Spring 会把查询串里同名的参数逐个
     * 塞进 EmpQueryDTO 的字段，一个都不传也没关系，DTO 里都有默认值。
     *
     * 参数上的 @Valid 必须写：它才会触发 DTO 里的 @Min(1) 等校验注解，
     * 把 page=0、pageSize=-5 这类参数拦成业务错误码。不写的话注解只是摆设。
     */
    @GetMapping
    @Operation(summary = "条件分页查询员工")
    public Result<PageResult<EmpVO>> page(@Valid EmpQueryDTO query) {
        return Result.success(empService.page(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 id 查询员工（含工作经历）")
    public Result<EmpDetailVO> getById(@PathVariable("id") Long id) {
        return Result.success(empService.getDetailById(id));
    }

    @Log
    @PostMapping
    @Operation(summary = "新增员工")
    public Result<Void> save(@Valid @RequestBody EmpDTO empDTO) {
        empService.save(empDTO);
        return Result.success();
    }

    @Log
    @PutMapping
    @Operation(summary = "修改员工")
    public Result<Void> update(@Valid @RequestBody EmpDTO empDTO) {
        empService.update(empDTO);
        return Result.success();
    }

    /**
     * 批量删除，id 用逗号分隔：DELETE /api/emps?ids=1,2,3
     *
     * required = false 是故意的：如果写成必填，前端不传 ids 时 Spring 会抛
     * MissingServletRequestParameterException，那个异常没人单独处理，
     * 会掉进兜底分支变成「系统繁忙，请稍后重试」。
     * 交给 Service 判断，才能给出「请选择要删除的员工」这种能看懂的话。
     */
    @Log
    @DeleteMapping
    @Operation(summary = "批量删除员工")
    public Result<Void> delete(@RequestParam(name = "ids", required = false) List<Long> ids) {
        empService.deleteByIds(ids);
        return Result.success();
    }

    /**
     * 导出 Excel。
     *
     * 三个和别的接口不一样的地方：
     *
     * 1. 返回类型是 void，不是 Result。它把 xlsx 的二进制直接写进响应体了，
     *    没有统一 JSON 包装——前端拿到的是文件流，不是 {code, msg, data}。
     *
     * 2. 筛选条件跟列表接口完全共用 EmpQueryDTO，但**不看 page / pageSize**：
     *    导出的是全部符合条件的数据，不是当前那一页。分页导出会让人以为
     *    导全了，实际只有 10 条，这是很容易犯的错。
     *
     * 3. 路径 /emps/export 和 /emps/{id} 看着会打架，其实不会：
     *    Spring 匹配时「写死的路径段」优先于「{占位符}」，
     *    所以 /emps/export 一定走这里，不会跑去按 id="export" 查员工。
     */
    @GetMapping("/export")
    @Operation(summary = "导出员工数据（Excel）")
    public void export(EmpQueryDTO query, HttpServletResponse response) throws IOException {
        String fileName = "员工表";
        if (query.getDeptId() != null) {
            fileName = deptService.getById(query.getDeptId()).getName() + "-员工名单";
        }
        ExcelUtil.write(response, empService.listForExport(query), EmpExportVO.class, fileName);
    }

}
