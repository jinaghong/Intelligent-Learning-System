package com.hjh.tlias.controller;

import com.hjh.tlias.annotation.Log;
import com.hjh.tlias.common.result.PageResult;
import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.dto.StudentDTO;
import com.hjh.tlias.dto.StudentQueryDTO;
import com.hjh.tlias.dto.ViolationDTO;
import com.hjh.tlias.service.StudentService;
import com.hjh.tlias.vo.StudentDetailVO;
import com.hjh.tlias.vo.StudentVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学员管理。比前两个模块多一个违纪处理的子接口。
 */
@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
@Tag(name = "学员管理")
public class StudentController {

    private final StudentService studentService;

    @GetMapping
    @Operation(summary = "条件分页查询学员")
    public Result<PageResult<StudentVO>> page(@Valid StudentQueryDTO query) {
        return Result.success(studentService.page(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 id 查询学员（含违纪明细）")
    public Result<StudentDetailVO> getById(@PathVariable("id") Long id) {
        return Result.success(studentService.getDetailById(id));
    }

    @Log
    @PostMapping
    @Operation(summary = "新增学员")
    public Result<Void> save(@Valid @RequestBody StudentDTO studentDTO) {
        studentService.save(studentDTO);
        return Result.success();
    }

    @Log
    @PutMapping
    @Operation(summary = "修改学员")
    public Result<Void> update(@Valid @RequestBody StudentDTO studentDTO) {
        studentService.update(studentDTO);
        return Result.success();
    }

    /** 批量删除，id 用逗号分隔：DELETE /api/students?ids=1,2,3 */
    @Log
    @DeleteMapping
    @Operation(summary = "批量删除学员")
    public Result<Void> delete(@RequestParam(name = "ids", required = false) List<Long> ids) {
        studentService.deleteByIds(ids);
        return Result.success();
    }

    /**
     * 违纪处理：给学员记一笔违纪（扣分）。
     * 明细插入和汇总字段累加在同一个事务里，由 Service 保证。
     */
    @Log
    @PostMapping("/violation")
    @Operation(summary = "违纪处理（记违纪 + 累加汇总）")
    public Result<Void> addViolation(@Valid @RequestBody ViolationDTO violationDTO) {
        studentService.addViolation(violationDTO);
        return Result.success();
    }

}
