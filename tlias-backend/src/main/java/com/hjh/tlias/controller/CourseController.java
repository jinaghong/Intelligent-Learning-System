package com.hjh.tlias.controller;

import com.hjh.tlias.annotation.Log;
import com.hjh.tlias.common.result.Result;
import com.hjh.tlias.dto.CourseDTO;
import com.hjh.tlias.entity.Course;
import com.hjh.tlias.service.CourseService;
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
 * 课程管理。五个接口都和 DeptController 一一对应，
 * 唯一多出来的业务规则（删除前数成绩）在 Service 里。
 */
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
@Tag(name = "课程管理")
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    @Operation(summary = "查询全部课程")
    public Result<List<Course>> list() {
        return Result.success(courseService.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 id 查询课程")
    public Result<Course> getById(@PathVariable("id") Long id) {
        return Result.success(courseService.getById(id));
    }

    @Log
    @PostMapping
    @Operation(summary = "新增课程")
    public Result<Void> save(@Valid @RequestBody CourseDTO courseDTO) {
        courseService.save(courseDTO);
        return Result.success();
    }

    @Log
    @PutMapping
    @Operation(summary = "修改课程")
    public Result<Void> update(@Valid @RequestBody CourseDTO courseDTO) {
        courseService.update(courseDTO);
        return Result.success();
    }

    @Log
    @DeleteMapping("/{id}")
    @Operation(summary = "删除课程")
    public Result<Void> delete(@PathVariable("id") Long id) {
        courseService.deleteById(id);
        return Result.success();
    }

}
