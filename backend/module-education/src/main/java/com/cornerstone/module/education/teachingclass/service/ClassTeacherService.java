package com.cornerstone.module.education.teachingclass.service;

import com.cornerstone.module.education.domain.entity.ClassTeacherEntity;
import com.cornerstone.module.education.teachingclass.dto.ClassTeacherCreateRequest;
import java.util.List;

public interface ClassTeacherService {

    List<ClassTeacherEntity> listClassTeachers(Long classId, Long teacherId);

    ClassTeacherEntity createClassTeacher(ClassTeacherCreateRequest request);

    ClassTeacherEntity updateClassTeacher(Long id, ClassTeacherCreateRequest request);

    void deleteClassTeacher(Long id);

    /**
     * 按班级主档的「主讲教师」同步班级教师关系：指定教师时把该教师升级为主讲（班级下没有该教师的任何关系记录时新建一条），
     * teacherId 为空表示撤销主讲。班级主档的 teacherId 始终以关系表反写为准。
     */
    void assignPrimaryTeacher(Long institutionId, Long classId, Long teacherId);
}
