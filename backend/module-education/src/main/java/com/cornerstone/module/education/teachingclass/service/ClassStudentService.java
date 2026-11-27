package com.cornerstone.module.education.teachingclass.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentPageQuery;
import com.cornerstone.module.education.teachingclass.dto.ClassStudentView;
import java.util.List;

public interface ClassStudentService {

    List<ClassStudentEntity> listClassStudents(Long classId, Long studentId);

    PageResult<ClassStudentView> pageClassStudents(ClassStudentPageQuery query);

    /** 指定班级的在班(active)人数,与入班容量校验同一口径 */
    int countActiveStudents(Long institutionId, Long classId);

    ClassStudentEntity createClassStudent(ClassStudentCreateRequest request);

    void deleteClassStudent(Long id);
}
