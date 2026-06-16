package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.ClassTeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.ClassTeacherCreateRequest;
import com.jiaopei.platform.education.mapper.ClassTeacherMapper;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.ClassChangeNoticeService;
import com.jiaopei.platform.education.service.ClassTeacherService;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ClassTeacherServiceImpl implements ClassTeacherService {

    private final ClassTeacherMapper classTeacherMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final TeacherMapper teacherMapper;
    private final InstitutionMapper institutionMapper;
    private final ClassChangeNoticeService classChangeNoticeService;

    public ClassTeacherServiceImpl(ClassTeacherMapper classTeacherMapper, TeachingClassMapper teachingClassMapper,
                                   TeacherMapper teacherMapper, InstitutionMapper institutionMapper,
                                   ClassChangeNoticeService classChangeNoticeService) {
        this.classTeacherMapper = classTeacherMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.teacherMapper = teacherMapper;
        this.institutionMapper = institutionMapper;
        this.classChangeNoticeService = classChangeNoticeService;
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

    private ClassTeacherEntity saveClassTeacher(Long id, ClassTeacherCreateRequest request) {
        requireInstitution(request.institutionId());
        TeachingClassEntity teachingClass = requireClassInInstitution(request.classId(), request.institutionId());
        Long previousPrimaryTeacherId = teachingClass.getTeacherId();
        requireTeacherInInstitution(request.teacherId(), request.institutionId());
        if (request.joinedAt() != null && request.leftAt() != null && request.leftAt().isBefore(request.joinedAt())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "leftAt cannot be earlier than joinedAt");
        }

        String teacherRole = normalizeTeacherRole(request.teacherRole());
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
                    "Class teacher relation already exists for class/teacher/role/joinedAt");
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
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + institutionId);
        }
    }

    private TeachingClassEntity requireClassInInstitution(Long classId, Long institutionId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Class does not exist: " + classId);
        }
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Class does not belong to institution: " + institutionId);
        }
        return teachingClass;
    }

    private void requireTeacherInInstitution(Long teacherId, Long institutionId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Teacher does not exist: " + teacherId);
        }
        if (!Objects.equals(teacher.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Teacher does not belong to institution: " + institutionId);
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
                "Class teacher relation already exists for class/teacher/role/joinedAt");
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
            throw new BusinessException(ResultCode.NOT_FOUND, "Class teacher does not exist: " + id);
        }
        return entity;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeTeacherRole(String teacherRole) {
        if (isBlank(teacherRole)) {
            return "lead";
        }
        String normalized = teacherRole.trim();
        if ("main".equalsIgnoreCase(normalized)) {
            return "lead";
        }
        return normalized;
    }
}
