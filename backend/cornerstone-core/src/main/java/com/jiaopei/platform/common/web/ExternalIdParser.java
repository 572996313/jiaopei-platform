package com.jiaopei.platform.common.web;

import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import java.util.Locale;
import java.util.Map;

public final class ExternalIdParser {

    /**
     * 字段名到中文名的映射，用于生成面向用户的报错文案；
     * 覆盖驼峰与下划线两种写法（客户端实际传哪种取决于 @JsonAlias）。
     */
    private static final Map<String, String> FIELD_NAME_ZH = Map.ofEntries(
        Map.entry("institutionid", "机构ID"),
        Map.entry("institution_id", "机构ID"),
        Map.entry("campusid", "校区ID"),
        Map.entry("campus_id", "校区ID"),
        Map.entry("termid", "学期ID"),
        Map.entry("term_id", "学期ID"),
        Map.entry("courseid", "课程ID"),
        Map.entry("course_id", "课程ID"),
        Map.entry("categoryid", "课程分类ID"),
        Map.entry("category_id", "课程分类ID"),
        Map.entry("classtypeid", "班级类型ID"),
        Map.entry("class_type_id", "班级类型ID"),
        Map.entry("classid", "班级ID"),
        Map.entry("class_id", "班级ID"),
        Map.entry("studentid", "学员ID"),
        Map.entry("student_id", "学员ID"),
        Map.entry("parentid", "家长ID"),
        Map.entry("parent_id", "家长ID"),
        Map.entry("teacherid", "教师ID"),
        Map.entry("teacher_id", "教师ID"),
        Map.entry("lessonid", "课次ID"),
        Map.entry("lesson_id", "课次ID"),
        Map.entry("relatedlessonid", "关联的原课次"),
        Map.entry("related_lesson_id", "关联的原课次"),
        Map.entry("classroomid", "教室ID"),
        Map.entry("classroom_id", "教室ID"),
        Map.entry("timeslotid", "时段ID"),
        Map.entry("time_slot_id", "时段ID"),
        Map.entry("roleid", "角色ID"),
        Map.entry("role_id", "角色ID"),
        Map.entry("menuid", "菜单ID"),
        Map.entry("menu_id", "菜单ID")
    );

    private ExternalIdParser() {
    }

    public static Long parseOptional(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequired(value, fieldName);
    }

    public static Long parseRequired(String value, String fieldName) {
        String display = displayFieldName(fieldName);
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, display + "不能为空");
        }

        String numeric = normalized.matches("\\d+")
            ? normalized
            : normalized.replaceFirst("^.*?(\\d+)$", "$1");
        if (!numeric.matches("\\d+")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, display + "必须是数字ID或以数字结尾的业务编码");
        }

        try {
            return Long.valueOf(numeric);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, display + "超出有效范围");
        }
    }

    private static String displayFieldName(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return "字段";
        }
        String key = fieldName.trim().toLowerCase(Locale.ROOT);
        return FIELD_NAME_ZH.getOrDefault(key, fieldName);
    }
}
