package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.ClassStudentEntity;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.ClassStudentCreateRequest;
import com.jiaopei.platform.education.mapper.ClassStudentMapper;
import com.jiaopei.platform.education.mapper.StudentMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.ClassStudentService;
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "Class and student must belong to institution: " + institutionId);
        }
        if (student.getCampusId() != null && !Objects.equals(student.getCampusId(), teachingClass.getCampusId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student campus does not match class campus");
        }
        if (request.leftAt() != null && request.joinedAt() != null && request.leftAt().isBefore(request.joinedAt())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "leftAt cannot be earlier than joinedAt");
        }
        assertUniqueClassStudent(request.classId(), request.studentId());
        assertClassCapacity(teachingClass);

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
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student already joined class: " + request.classId());
        }

        int currentStudents = teachingClass.getCurrentStudents() == null ? 0 : teachingClass.getCurrentStudents();
        teachingClass.setCurrentStudents(currentStudents + 1);
        teachingClassMapper.updateById(teachingClass);
        return entity;
    }

    private TeachingClassEntity requireClass(Long classId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Class does not exist: " + classId);
        }
        return teachingClass;
    }

    private StudentEntity requireStudent(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Student does not exist: " + studentId);
        }
        return student;
    }

    private void assertUniqueClassStudent(Long classId, Long studentId) {
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStudentId, studentId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Student already joined class: " + classId);
        }
    }

    private void assertClassCapacity(TeachingClassEntity teachingClass) {
        int capacity = teachingClass.getCapacityLimit() == null ? 0 : teachingClass.getCapacityLimit();
        int currentStudents = teachingClass.getCurrentStudents() == null ? 0 : teachingClass.getCurrentStudents();
        if (capacity > 0 && currentStudents >= capacity) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Class capacity is full: " + teachingClass.getId());
        }
    }
}
