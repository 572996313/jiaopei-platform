package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ClassTeacherEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ClassTeacherCreateRequest;
import com.cornerstone.module.education.mapper.ClassTeacherMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ClassChangeNoticeService;
import com.cornerstone.module.education.service.ClassTeacherService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassTeacherServiceImpl implements ClassTeacherService {

    /** 教师角色字典(teacher_role)里代表主讲的取值，班级主档指定主讲教师时按它写入关系表 */
    private static final String PRIMARY_TEACHER_ROLE = "lead";

    private final ClassTeacherMapper classTeacherMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final TeacherMapper teacherMapper;
    private final InstitutionMapper institutionMapper;
    private final ClassChangeNoticeService classChangeNoticeService;
    private final DictValueValidator dictValueValidator;

    public ClassTeacherServiceImpl(ClassTeacherMapper classTeacherMapper, TeachingClassMapper teachingClassMapper,
                                   TeacherMapper teacherMapper, InstitutionMapper institutionMapper,
                                   ClassChangeNoticeService classChangeNoticeService,
                                   DictValueValidator dictValueValidator) {
        this.classTeacherMapper = classTeacherMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.teacherMapper = teacherMapper;
        this.institutionMapper = institutionMapper;
        this.classChangeNoticeService = classChangeNoticeService;
        this.dictValueValidator = dictValueValidator;
    }

    @Override
    public List<ClassTeacherEntity> listClassTeachers(Long classId, Long teacherId) {
        LambdaQueryWrapper<ClassTeacherEntity> wrapper = new LambdaQueryWrapper<ClassTeacherEntity>()
            .orderByAsc(ClassTeacherEntity::getClassId)
            .orderByAsc(ClassTeacherEntity::getTeacherId)
            .orderByAsc(ClassTeacherEntity::getJoinedAt);
        if (classId != null) {
            wrapper.eq(ClassTeacherEntity::getClassId, classId);
        }
        if (teacherId != null) {
            wrapper.eq(ClassTeacherEntity::getTeacherId, teacherId);
        }
        return classTeacherMapper.selectList(wrapper);
    }

    @Override
    public ClassTeacherEntity createClassTeacher(ClassTeacherCreateRequest request) {
        return saveClassTeacher(null, request);
    }

    @Override
    public ClassTeacherEntity updateClassTeacher(Long id, ClassTeacherCreateRequest request) {
        requireClassTeacher(id);
        return saveClassTeacher(id, request);
    }

    @Override
    public void deleteClassTeacher(Long id) {
        ClassTeacherEntity entity = requireClassTeacher(id);
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(entity.getClassId());
        Long previousPrimaryTeacherId = teachingClass == null ? null : teachingClass.getTeacherId();
        classTeacherMapper.deleteById(id);
        if (teachingClass != null) {
            ClassTeacherEntity currentPrimary = syncPrimaryTeacher(teachingClass);
            syncTeachingClassPrimaryTeacher(teachingClass, currentPrimary);
            if (Boolean.TRUE.equals(entity.getIsPrimary()) && previousPrimaryTeacherId != null) {
                if (currentPrimary != null && !Objects.equals(previousPrimaryTeacherId, currentPrimary.getTeacherId())) {
                    classChangeNoticeService.recordPrimaryTeacherChanged(teachingClass, currentPrimary.getId(),
                        "班级主讲教师已自动切换到新的有效教师。", "请确认排课、课表与授课安排是否需要同步调整。");
                }
            }
            classChangeNoticeService.syncClassNotices(teachingClass.getInstitutionId(), teachingClass.getId());
        }
    }

    @Override
    @Transactional
    public void assignPrimaryTeacher(Long institutionId, Long classId, Long teacherId) {
        TeachingClassEntity teachingClass = requireClassInInstitution(classId, institutionId);
        Long previousPrimaryTeacherId = teachingClass.getTeacherId();
        if (teacherId == null) {
            if (previousPrimaryTeacherId == null) {
                return;
            }
            demoteOtherPrimaryTeachers(classId, null);
            // updateById 会忽略 null，把主讲教师清空必须走 UpdateWrapper 显式 set
            teachingClassMapper.update(null, new LambdaUpdateWrapper<TeachingClassEntity>()
                .eq(TeachingClassEntity::getId, classId)
                .set(TeachingClassEntity::getTeacherId, null));
            return;
        }
        if (Objects.equals(previousPrimaryTeacherId, teacherId) && hasPrimaryRelation(classId, teacherId)) {
            return;
        }
        requireTeacherInInstitution(teacherId, institutionId);
        ClassTeacherEntity relation = firstClassTeacher(classId, teacherId);
        if (relation == null) {
            relation = new ClassTeacherEntity();
            relation.setInstitutionId(institutionId);
            relation.setClassId(classId);
            relation.setTeacherId(teacherId);
            relation.setTeacherRole(PRIMARY_TEACHER_ROLE);
            relation.setIsPrimary(Boolean.TRUE);
            relation.setStatus("active");
            relation.setDeleted(Boolean.FALSE);
            classTeacherMapper.insert(relation);
        } else {
            relation.setTeacherRole(PRIMARY_TEACHER_ROLE);
            relation.setIsPrimary(Boolean.TRUE);
            classTeacherMapper.updateById(relation);
        }
        demoteOtherPrimaryTeachers(classId, relation.getId());
        syncTeachingClassPrimaryTeacher(teachingClass, relation);
        if (previousPrimaryTeacherId != null && !Objects.equals(previousPrimaryTeacherId, teacherId)) {
            classChangeNoticeService.recordPrimaryTeacherChanged(teachingClass, relation.getId(),
                "班级主讲教师已切换。", "请检查后续排课、课次执行和代课安排是否需要同步调整。");
        }
    }

    private ClassTeacherEntity saveClassTeacher(Long id, ClassTeacherCreateRequest request) {
        requireInstitution(request.institutionId());
        TeachingClassEntity teachingClass = requireClassInInstitution(request.classId(), request.institutionId());
        Long previousPrimaryTeacherId = teachingClass.getTeacherId();
        requireTeacherInInstitution(request.teacherId(), request.institutionId());
        if (request.joinedAt() != null && request.leftAt() != null && request.leftAt().isBefore(request.joinedAt())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "离开日期不能早于加入日期");
        }

        String validatedRole = dictValueValidator.validate("teacher_role", "教师角色", request.teacherRole());
        String teacherRole = validatedRole == null ? PRIMARY_TEACHER_ROLE : validatedRole;
        if (Boolean.TRUE.equals(request.isPrimary())) {
            demoteOtherPrimaryTeachers(request.classId(), id);
        }
        assertUniqueTeacherPeriod(request.classId(), request.teacherId(), teacherRole, request.joinedAt(), id);

        ClassTeacherEntity entity = id == null ? new ClassTeacherEntity() : requireClassTeacher(id);
        entity.setId(id);
        entity.setInstitutionId(request.institutionId());
        entity.setClassId(request.classId());
        entity.setTeacherId(request.teacherId());
        entity.setTeacherRole(teacherRole);
        entity.setIsPrimary(Boolean.TRUE.equals(request.isPrimary()));
        entity.setJoinedAt(request.joinedAt());
        entity.setLeftAt(request.leftAt());
        entity.setStatus(isBlank(request.status()) ? "active" : request.status().trim());
        entity.setRemark(request.remark());
        if (id == null) {
            entity.setDeleted(Boolean.FALSE);
            try {
                classTeacherMapper.insert(entity);
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "该班级/教师/角色/入任日期的班级教师关系已存在");
            }
        } else {
            classTeacherMapper.updateById(entity);
        }
        ClassTeacherEntity currentPrimary = syncPrimaryTeacher(teachingClass);
        syncTeachingClassPrimaryTeacher(teachingClass, currentPrimary);
        if (previousPrimaryTeacherId != null
            && currentPrimary != null
            && !Objects.equals(previousPrimaryTeacherId, currentPrimary.getTeacherId())) {
            classChangeNoticeService.recordPrimaryTeacherChanged(teachingClass, currentPrimary.getId(),
                "班级主讲教师已切换。", "请检查后续排课、课次执行和代课安排是否需要同步调整。");
        }
        classChangeNoticeService.syncClassNotices(teachingClass.getInstitutionId(), teachingClass.getId());
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
    }

    private TeachingClassEntity requireClassInInstitution(Long classId, Long institutionId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + classId);
        }
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级不属于该机构：" + institutionId);
        }
        return teachingClass;
    }

    private void requireTeacherInInstitution(Long teacherId, Long institutionId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "教师不存在：" + teacherId);
        }
        if (!Objects.equals(teacher.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "教师不属于该机构：" + institutionId);
        }
    }

    private void assertUniqueTeacherPeriod(Long classId, Long teacherId, String teacherRole, java.time.LocalDate joinedAt,
                                           Long excludeId) {
        LambdaQueryWrapper<ClassTeacherEntity> wrapper = new LambdaQueryWrapper<ClassTeacherEntity>()
            .eq(ClassTeacherEntity::getClassId, classId)
            .eq(ClassTeacherEntity::getTeacherId, teacherId)
            .eq(ClassTeacherEntity::getTeacherRole, teacherRole);
        if (joinedAt == null) {
            wrapper.isNull(ClassTeacherEntity::getJoinedAt);
        } else {
            wrapper.eq(ClassTeacherEntity::getJoinedAt, joinedAt);
        }
        if (excludeId != null) {
            wrapper.ne(ClassTeacherEntity::getId, excludeId);
        }
        Long count = classTeacherMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该班级/教师/角色/入任日期的班级教师关系已存在");
        }
    }

    private void demoteOtherPrimaryTeachers(Long classId, Long excludeId) {
        LambdaQueryWrapper<ClassTeacherEntity> wrapper = new LambdaQueryWrapper<ClassTeacherEntity>()
            .eq(ClassTeacherEntity::getClassId, classId)
            .eq(ClassTeacherEntity::getIsPrimary, Boolean.TRUE);
        if (excludeId != null) {
            wrapper.ne(ClassTeacherEntity::getId, excludeId);
        }
        List<ClassTeacherEntity> existingPrimaryTeachers = classTeacherMapper.selectList(wrapper);
        for (ClassTeacherEntity existingPrimaryTeacher : existingPrimaryTeachers) {
            existingPrimaryTeacher.setIsPrimary(Boolean.FALSE);
            classTeacherMapper.updateById(existingPrimaryTeacher);
        }
    }

    private boolean hasPrimaryRelation(Long classId, Long teacherId) {
        Long count = classTeacherMapper.selectCount(new LambdaQueryWrapper<ClassTeacherEntity>()
            .eq(ClassTeacherEntity::getClassId, classId)
            .eq(ClassTeacherEntity::getTeacherId, teacherId)
            .eq(ClassTeacherEntity::getIsPrimary, Boolean.TRUE));
        return count != null && count > 0;
    }

    /** 同一教师在同一班级下可能有多条不同角色/任期的关系，取最早的那条设为主讲 */
    private ClassTeacherEntity firstClassTeacher(Long classId, Long teacherId) {
        List<ClassTeacherEntity> relations = classTeacherMapper.selectList(new LambdaQueryWrapper<ClassTeacherEntity>()
            .eq(ClassTeacherEntity::getClassId, classId)
            .eq(ClassTeacherEntity::getTeacherId, teacherId)
            .orderByAsc(ClassTeacherEntity::getJoinedAt)
            .orderByAsc(ClassTeacherEntity::getId));
        return relations.isEmpty() ? null : relations.get(0);
    }

    private ClassTeacherEntity syncPrimaryTeacher(TeachingClassEntity teachingClass) {
        ClassTeacherEntity primaryTeacher = classTeacherMapper.selectOne(new LambdaQueryWrapper<ClassTeacherEntity>()
            .eq(ClassTeacherEntity::getClassId, teachingClass.getId())
            .eq(ClassTeacherEntity::getIsPrimary, Boolean.TRUE)
            .last("LIMIT 1"));
        if (primaryTeacher != null) {
            return primaryTeacher;
        }

        ClassTeacherEntity fallbackTeacher = findFallbackTeacher(teachingClass.getId());
        if (fallbackTeacher != null) {
            fallbackTeacher.setIsPrimary(Boolean.TRUE);
            classTeacherMapper.updateById(fallbackTeacher);
        }
        return fallbackTeacher;
    }

    private void syncTeachingClassPrimaryTeacher(TeachingClassEntity teachingClass, ClassTeacherEntity currentPrimary) {
        Long nextTeacherId = currentPrimary == null ? null : currentPrimary.getTeacherId();
        if (!Objects.equals(teachingClass.getTeacherId(), nextTeacherId)) {
            teachingClass.setTeacherId(nextTeacherId);
            teachingClassMapper.updateById(teachingClass);
        }
    }

    private ClassTeacherEntity findFallbackTeacher(Long classId) {
        List<ClassTeacherEntity> candidates = classTeacherMapper.selectList(new LambdaQueryWrapper<ClassTeacherEntity>()
            .eq(ClassTeacherEntity::getClassId, classId)
            .eq(ClassTeacherEntity::getStatus, "active")
            .orderByAsc(ClassTeacherEntity::getJoinedAt)
            .orderByAsc(ClassTeacherEntity::getId));
        LocalDate today = LocalDate.now();
        for (ClassTeacherEntity candidate : candidates) {
            if (candidate.getLeftAt() == null || !candidate.getLeftAt().isBefore(today)) {
                return candidate;
            }
        }
        return null;
    }

    private ClassTeacherEntity requireClassTeacher(Long id) {
        ClassTeacherEntity entity = classTeacherMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级教师关系不存在：" + id);
        }
        return entity;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
