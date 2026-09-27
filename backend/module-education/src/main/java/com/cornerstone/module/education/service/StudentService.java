package com.cornerstone.module.education.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.dto.StudentCreateRequest;
import com.cornerstone.module.education.dto.StudentPageQuery;
import com.cornerstone.module.education.dto.StudentOptionView;
import com.cornerstone.module.education.dto.StudentUpdateRequest;
import java.util.List;

public interface StudentService {

    List<StudentEntity> listStudents(Long institutionId, Long campusId, String keyword);

    /**
     * 学员下拉选项:关键字模糊匹配姓名/学号,最多返回 OPTION_LIMIT 条。
     * 全量学员可达数千条,下拉一次性渲染会把页面卡住,所以只按需取一屏。
     */
    List<StudentOptionView> listStudentOptions(Long institutionId, Long campusId, String keyword);

    PageResult<StudentEntity> pageStudents(StudentPageQuery query);

    StudentEntity createStudent(StudentCreateRequest request);

    StudentEntity updateStudent(Long id, StudentUpdateRequest request);

    void deleteStudent(Long id);
}
