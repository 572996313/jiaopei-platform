package com.cornerstone.module.education.config;

import com.cornerstone.core.common.web.ExternalIdFieldLabelContributor;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 教务模块的字段中文名，供底座拼报错文案。
 *
 * <p>随模块开关一起装配：关闭 education 后这些词不进词表，报错回落成原始字段名。</p>
 */
@Component
public class EducationExternalIdLabels implements ExternalIdFieldLabelContributor {

    @Override
    public Map<String, String> labels() {
        return Map.of(
            "courseId", "课程ID",
            "categoryId", "课程分类ID",
            "classTypeId", "班级类型ID",
            "classId", "班级ID",
            "studentId", "学员ID",
            "parentId", "家长ID",
            "teacherId", "教师ID",
            "lessonId", "课次ID",
            "relatedLessonId", "关联的原课次");
    }
}
