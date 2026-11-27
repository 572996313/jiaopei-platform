package com.cornerstone.module.education.student.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.student.dto.StudentCreateRequest;
import com.cornerstone.module.education.student.dto.StudentOptionView;
import com.cornerstone.module.education.student.dto.StudentPageQuery;
import com.cornerstone.module.education.student.dto.StudentUpdateRequest;
import java.util.List;

public interface StudentService {

    List<StudentEntity> listStudents(Long institutionId, String keyword);

    /**
     * 学员下拉选项:关键字模糊匹配姓名/学号,最多返回 OPTION_LIMIT 条。
     * 全量学员可达数千条,下拉一次性渲染会把页面卡住,所以只按需取一屏。
     * 传 ids 时按 id 批量回查(供表格行补名称),此时忽略关键字。
     */
    List<StudentOptionView> listStudentOptions(Long institutionId, String keyword, List<Long> ids);

    PageResult<StudentEntity> pageStudents(StudentPageQuery query);

    StudentEntity createStudent(StudentCreateRequest request);

    StudentEntity updateStudent(Long id, StudentUpdateRequest request);

    void deleteStudent(Long id);
}
