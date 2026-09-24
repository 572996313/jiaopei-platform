package com.cornerstone.module.education.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.dto.TeacherCreateRequest;
import com.cornerstone.module.education.dto.TeacherPageQuery;
import com.cornerstone.module.education.dto.TeacherUpdateRequest;
import java.util.List;

public interface TeacherService {

    List<TeacherEntity> listTeachers(Long institutionId, Long campusId);

    PageResult<TeacherEntity> pageTeachers(TeacherPageQuery query);

    /**
     * 按与分页列表相同的筛选条件导出全部教师档案（不分页）。
     *
     * <p>导出的取值一律用规范值（字典 item_value、校区编码），与导入模板列对齐，
     * 改完能原样导回去；中文标签只在页面上按字典回显。</p>
     */
    byte[] exportTeachers(TeacherPageQuery query);

    TeacherEntity createTeacher(TeacherCreateRequest request);

    TeacherEntity updateTeacher(Long id, TeacherUpdateRequest request);

    void deleteTeacher(Long id);
}
