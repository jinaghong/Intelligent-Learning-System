package com.hjh.tlias.service;

import com.hjh.tlias.dto.CourseDTO;
import com.hjh.tlias.entity.Course;

import java.util.List;

public interface CourseService {

    List<Course> list();

    Course getById(Long id);

    void save(CourseDTO courseDTO);

    void update(CourseDTO courseDTO);

    void deleteById(Long id);

}
