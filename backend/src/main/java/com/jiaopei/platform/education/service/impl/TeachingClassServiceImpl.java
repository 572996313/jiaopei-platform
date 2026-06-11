package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.TeachingClassCreateRequest;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.TeachingClassService;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.domain.entity.TermEntity;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.mapper.TermMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TeachingClassServiceImpl implements TeachingClassService {

    private final TeachingClassMapper teachingClassMapper;
    private final InstitutionMapper institutionMapper;
    private final CampusMapper campusMapper;
    private final TermMapper termMapper;
    private final TeacherMapper teacherMapper;

    public TeachingClassServiceImpl(TeachingClassMapper teachingClassMapper, InstitutionMapper institutionMapper,
                                    CampusMapper campusMapper, TermMapper termMapper, TeacherMapper teacherMapper) {
        this.teachingClassMapper = teachingClassMapper;
        this.institutionMapper = institutionMapper;
        this.campusMapper = campusMapper;
        this.termMapper = termMapper;
        this.teacherMapper = teacherMapper;
    }

    @Override
    public List<TeachingClassEntity> listClasses(Long institutionId, Long campusId, Long termId) {
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .orderByAsc(TeachingClassEntity::getInstitutionId)
            .orderByAsc(TeachingClassEntity::getCampusId)
            .orderByAsc(TeachingClassEntity::getClassCode);
        if (institutionId != null) {
            wrapper.eq(TeachingClassEntity::getInstitutionId, institutionId);
        }
        if (campusId != null) {
            wrapper.eq(TeachingClassEntity::getCampusId, campusId);
        }
        if (termId != null) {
            wrapper.eq(TeachingClassEntity::getTermId, termId);
        }
        return teachingClassMapper.selectList(wrapper);
    }

    @Override
    public TeachingClassEntity createClass(TeachingClassCreateRequest request) {
        requireInstitution(request.institutionId());
        requireCampusInInstitution(request.campusId(), request.institutionId());
        if (request.termId() != null) {
            requireTermInInstitution(request.termId(), request.institutionId());
        }
        if (request.teacherId() != null) {
            requireTeacherInInstitution(request.teacherId(), request.institutionId());
        }
        int capacityLimit = request.capacityLimit() == null ? 0 : request.capacityLimit();
        int currentStudents = request.currentStudents() == null ? 0 : request.currentStudents();
        if (currentStudents > capacityLimit) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "currentStudents cannot exceed capacityLimit");
        }

        String classCode = normalize(request.classCode());
        String className = normalize(request.className());
        assertUniqueClassCode(request.campusId(), classCode);
        assertUniqueClassName(request.campusId(), className);

        TeachingClassEntity entity = new TeachingClassEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setTermId(request.termId());
        entity.setClassCode(classCode);
        entity.setClassName(className);
        entity.setTeacherId(request.teacherId());
        entity.setCapacityLimit(capacityLimit);
        entity.setCurrentStudents(currentStudents);
        entity.setStatus(request.status() == null || request.status().isBlank() ? "planning" : request.status());
        entity.setOpenedAt(request.openedAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            teachingClassMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Class classCode or className already exists in campus: " + request.campusId());
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + institutionId);
        }
    }

    private void requireCampusInInstitution(Long campusId, Long institutionId) {
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Campus does not exist: " + campusId);
        }
        if (!Objects.equals(campus.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Campus does not belong to institution: " + institutionId);
        }
    }

    private void requireTermInInstitution(Long termId, Long institutionId) {
        TermEntity term = termMapper.selectById(termId);
        if (term == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Term does not exist: " + termId);
        }
        if (!Objects.equals(term.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Term does not belong to institution: " + institutionId);
        }
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

    private void assertUniqueClassCode(Long campusId, String classCode) {
        Long count = teachingClassMapper.selectCount(new LambdaQueryWrapper<TeachingClassEntity>()
            .eq(TeachingClassEntity::getCampusId, campusId)
            .eq(TeachingClassEntity::getClassCode, classCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Class classCode already exists in campus: " + campusId);
        }
    }

    private void assertUniqueClassName(Long campusId, String className) {
        Long count = teachingClassMapper.selectCount(new LambdaQueryWrapper<TeachingClassEntity>()
            .eq(TeachingClassEntity::getCampusId, campusId)
            .eq(TeachingClassEntity::getClassName, className));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Class className already exists in campus: " + campusId);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
