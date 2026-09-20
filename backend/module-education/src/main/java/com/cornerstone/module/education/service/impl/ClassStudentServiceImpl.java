package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ClassStudentService;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassStudentServiceImpl implements ClassStudentService {

    private final ClassStudentMapper classStudentMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final StudentMapper studentMapper;

    public ClassStudentServiceImpl(ClassStudentMapper classStudentMapper, TeachingClassMapper teachingClassMapper,
                                   StudentMapper studentMapper) {
        this.classStudentMapper = classStudentMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.studentMapper = studentMapper;
    }

    @Override
    public List<ClassStudentEntity> listClassStudents(Long classId, Long studentId) {
        LambdaQueryWrapper<ClassStudentEntity> wrapper = new LambdaQueryWrapper<ClassStudentEntity>()
            .orderByAsc(ClassStudentEntity::getClassId)
            .orderByAsc(ClassStudentEntity::getStudentId);
        if (classId != null) {
            wrapper.eq(ClassStudentEntity::getClassId, classId);
        }
        if (studentId != null) {
            wrapper.eq(ClassStudentEntity::getStudentId, studentId);
        }
        return classStudentMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public ClassStudentEntity createClassStudent(ClassStudentCreateRequest request) {
        TeachingClassEntity teachingClass = requireClass(request.classId());
        StudentEntity student = requireStudent(request.studentId());
        Long institutionId = request.institutionId() == null ? teachingClass.getInstitutionId() : request.institutionId();
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId) || !Objects.equals(student.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级与学员必须属于同一机构：" + institutionId);
        }
        if (student.getCampusId() != null && !Objects.equals(student.getCampusId(), teachingClass.getCampusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学员校区与班级校区不一致");
        }
        if (request.leftAt() != null && request.joinedAt() != null && request.leftAt().isBefore(request.joinedAt())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "离开日期不能早于加入日期");
        }
        assertUniqueClassStudent(request.classId(), request.studentId());
        int activeStudents = countActiveStudents(request.classId());
        assertClassCapacity(teachingClass, activeStudents);

        ClassStudentEntity entity = new ClassStudentEntity();
        entity.setInstitutionId(institutionId);
        entity.setClassId(request.classId());
        entity.setStudentId(request.studentId());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "active" : request.status());
        entity.setJoinedAt(request.joinedAt() == null ? LocalDate.now() : request.joinedAt());
        entity.setLeftAt(request.leftAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            classStudentMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该学员已加入此班级：" + request.classId());
        }

        // 当前人数按在班学员关系重算，只统计 status=active 的记录，并顺带修复历史脏数据
        teachingClass.setCurrentStudents(countActiveStudents(request.classId()));
        teachingClassMapper.updateById(teachingClass);
        return entity;
    }

    @Override
    @Transactional
    public void deleteClassStudent(Long id) {
        ClassStudentEntity existing = classStudentMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级学员关系不存在：" + id);
        }
        classStudentMapper.deletePhysicallyById(id);
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(existing.getClassId());
        if (teachingClass != null) {
            teachingClass.setCurrentStudents(countActiveStudents(existing.getClassId()));
            teachingClassMapper.updateById(teachingClass);
        }
    }

    private int countActiveStudents(Long classId) {
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStatus, "active"));
        return count == null ? 0 : count.intValue();
    }

    private TeachingClassEntity requireClass(Long classId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + classId);
        }
        return teachingClass;
    }

    private StudentEntity requireStudent(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学员不存在：" + studentId);
        }
        return student;
    }

    private void assertUniqueClassStudent(Long classId, Long studentId) {
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStudentId, studentId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该学员已加入此班级：" + classId);
        }
    }

    private void assertClassCapacity(TeachingClassEntity teachingClass, int activeStudents) {
        int capacity = teachingClass.getCapacityLimit() == null ? 0 : teachingClass.getCapacityLimit();
        if (capacity > 0 && activeStudents >= capacity) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级容量已满：" + teachingClass.getId());
        }
    }
}
