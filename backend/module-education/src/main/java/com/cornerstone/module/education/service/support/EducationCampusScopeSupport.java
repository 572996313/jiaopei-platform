package com.cornerstone.module.education.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.EnrollmentEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.EnrollmentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 教务侧没有 campus_id 的表（学员/报名/班级学员/考勤）如何经班级推导校区范围。
 *
 * <p>学员可见 = 至少有一条班级或报名关联落在可访问校区；没有任何班级归属的学员一期视为不可见。
 * 写侧更严格：学员的所有班级关联都必须落在范围内，避免一个校区管理员改到另一个校区的共享档案。</p>
 */
public final class EducationCampusScopeSupport {

    private EducationCampusScopeSupport() {
    }

    /** 报名/班级学员/考勤：按 class_id 子查询收窄。 */
    public static <T> void applyClassVisible(LambdaQueryWrapper<T> wrapper,
                                             SFunction<T, Long> classIdColumn) {
        CampusScopeGuard.applyClassIds(wrapper, classIdColumn);
    }

    /** 学员列表：存在任一班级或报名关联落在可访问校区。 */
    public static void applyStudentVisible(LambdaQueryWrapper<StudentEntity> wrapper) {
        Set<Long> campusIds = CampusScopeGuard.campusIds();
        if (campusIds == null) {
            return;
        }
        Long institutionId = AuthContext.requireUser().orgId();
        if (campusIds.isEmpty() || institutionId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        String classSql = CampusScopeGuard.accessibleClassIdSql(institutionId, campusIds);
        wrapper.and(w -> w
            .exists("SELECT 1 FROM jp_class_student cs WHERE cs.student_id = jp_student.id"
                + " AND cs.deleted = 0 AND cs.class_id IN (" + classSql + ")")
            .or()
            .exists("SELECT 1 FROM jp_enrollment e WHERE e.student_id = jp_student.id"
                + " AND e.deleted = 0 AND e.class_id IN (" + classSql + ")"));
    }

    /** 只带 student_id 的关系表（家长/账号关联）：按学员可见性收窄。 */
    public static <T> void applyStudentIdVisible(LambdaQueryWrapper<T> wrapper,
                                                 SFunction<T, Long> studentIdColumn) {
        Set<Long> campusIds = CampusScopeGuard.campusIds();
        if (campusIds == null) {
            return;
        }
        Long institutionId = AuthContext.requireUser().orgId();
        if (campusIds.isEmpty() || institutionId == null) {
            wrapper.apply("1 = 0");
            return;
        }
        String classSql = CampusScopeGuard.accessibleClassIdSql(institutionId, campusIds);
        wrapper.inSql(studentIdColumn, "SELECT s.id FROM jp_student s WHERE s.deleted = 0 AND ("
            + "EXISTS (SELECT 1 FROM jp_class_student cs WHERE cs.student_id = s.id"
            + " AND cs.deleted = 0 AND cs.class_id IN (" + classSql + "))"
            + " OR EXISTS (SELECT 1 FROM jp_enrollment e WHERE e.student_id = s.id"
            + " AND e.deleted = 0 AND e.class_id IN (" + classSql + ")))");
    }

    /** 学员详情/编辑/删除：所有班级关联都必须在可访问校区内。 */
    public static void assertStudentWritable(ClassStudentMapper classStudentMapper,
                                             EnrollmentMapper enrollmentMapper,
                                             TeachingClassMapper teachingClassMapper,
                                             Long studentId) {
        Set<Long> campusIds = CampusScopeGuard.campusIds();
        if (campusIds == null) {
            return;
        }
        if (campusIds.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前角色没有任何可访问校区");
        }
        Set<Long> classIds = new LinkedHashSet<>();
        classStudentMapper.selectList(new LambdaQueryWrapper<ClassStudentEntity>()
                .eq(ClassStudentEntity::getStudentId, studentId))
            .forEach(item -> classIds.add(item.getClassId()));
        enrollmentMapper.selectList(new LambdaQueryWrapper<EnrollmentEntity>()
                .eq(EnrollmentEntity::getStudentId, studentId))
            .forEach(item -> classIds.add(item.getClassId()));
        if (classIds.isEmpty()) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                "该学员尚未归属任何班级，当前角色无权维护");
        }
        Long outside = teachingClassMapper.selectCount(new LambdaQueryWrapper<TeachingClassEntity>()
            .in(TeachingClassEntity::getId, classIds)
            .notIn(TeachingClassEntity::getCampusId, campusIds));
        if (outside != null && outside > 0) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                "该学员同时属于其他校区的班级，当前角色无权维护");
        }
    }

    /** 报名/班级学员/考勤写侧：目标班级必须在可访问校区内。 */
    public static void assertClassWritable(TeachingClassEntity teachingClass, String subject) {
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, subject + "不存在");
        }
        CampusScopeGuard.assertCampus(teachingClass.getCampusId(), subject);
    }

    /** 判断一组班级是否全部落在可访问校区，供批量写操作使用。 */
    public static void assertClassesWritable(List<TeachingClassEntity> classes, String subject) {
        Set<Long> campusIds = CampusScopeGuard.campusIds();
        if (campusIds == null) {
            return;
        }
        for (TeachingClassEntity teachingClass : classes) {
            if (!campusIds.contains(teachingClass.getCampusId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他校区的" + subject);
            }
        }
    }
}
