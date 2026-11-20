package com.cornerstone.module.education.config;

import com.cornerstone.core.system.code.CodeRuleContributor;
import com.cornerstone.core.system.code.CodeRuleSpec;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 教务模块的编号资源登记：教师、学员、班级、报名、课程、课程分类、班型、收费项目。
 *
 * <p>与组织模块同口径：行业名词只出现在登记方模块，底座不认得"学号""工号"。
 * 这里给的是默认格式与"编号存在哪张表哪一列"，操作员改过之后以 {@code sys_code_rule}
 * 的生效格式为准；没配规则的资源取号会被拒，所以本文件合入不改变现有页面行为。</p>
 *
 * <p>默认格式按各资源在教培现场的实际节奏定：**学号带年份**（一届一档，跨年从 0001 重排，
 * 号短且能一眼看出届别）；**班级与报名带年月**（开学季按月招，月内累加）；
 * **教师工号不带日期**（工号跟着人走一辈子，按年重排会让"0007 老师"在明年变成另一个 0007）；
 * **课程/分类/班型不带日期**（档案类表，数量稳定，编号要长期可辨认）。</p>
 */
@Component
public class EducationCodeRuleContributor implements CodeRuleContributor {

    @Override
    public List<CodeRuleSpec> specs() {
        return List.of(
            new CodeRuleSpec("teacher", "教师工号", "jp_teacher", "teacher_code",
                "TCH", "-", "", 4),
            new CodeRuleSpec("student", "学号", "jp_student", "student_no",
                "STU", "-", "yyyy", 4),
            new CodeRuleSpec("class", "班级编码", "jp_class", "class_code",
                "CLS", "-", "yyyyMM", 3),
            new CodeRuleSpec("enrollment", "报名编号", "jp_enrollment", "enrollment_no",
                "ENR", "-", "yyyyMM", 4),
            new CodeRuleSpec("course", "课程编号", "jp_course", "course_code",
                "CRS", "-", "", 3),
            new CodeRuleSpec("course_category", "课程分类编码", "jp_course_category", "category_code",
                "CAT", "-", "", 2),
            new CodeRuleSpec("class_type", "班型编码", "jp_class_type", "type_code",
                "TYP", "-", "", 2),
            new CodeRuleSpec("fee_item", "收费项目编码", "jp_fee_item", "item_code",
                "FE", "-", "", 3));
    }
}
