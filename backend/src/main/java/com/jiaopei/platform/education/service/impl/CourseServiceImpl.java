package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.CourseCategoryEntity;
import com.jiaopei.platform.education.domain.entity.CourseEntity;
import com.jiaopei.platform.education.dto.CourseCreateRequest;
import com.jiaopei.platform.education.dto.CourseUpdateRequest;
import com.jiaopei.platform.education.mapper.CourseCategoryMapper;
import com.jiaopei.platform.education.mapper.CourseMapper;
import com.jiaopei.platform.education.service.CourseService;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final CourseCategoryMapper courseCategoryMapper;
    private final InstitutionMapper institutionMapper;

    public CourseServiceImpl(CourseMapper courseMapper, CourseCategoryMapper courseCategoryMapper,
                             InstitutionMapper institutionMapper) {
        this.courseMapper = courseMapper;
        this.courseCategoryMapper = courseCategoryMapper;
        this.institutionMapper = institutionMapper;
    }

    @Override
    public List<CourseEntity> listCourses(Long institutionId, Long categoryId) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<CourseEntity>()
            .orderByAsc(CourseEntity::getInstitutionId)
            .orderByAsc(CourseEntity::getCategoryId)
            .orderByAsc(CourseEntity::getCourseCode);
        if (institutionId != null) {
            wrapper.eq(CourseEntity::getInstitutionId, institutionId);
        }
        if (categoryId != null) {
            wrapper.eq(CourseEntity::getCategoryId, categoryId);
        }
        return courseMapper.selectList(wrapper);
    }

    @Override
    public CourseEntity createCourse(CourseCreateRequest request) {
        requireInstitution(request.institutionId());
        if (request.categoryId() != null) {
            requireCategoryInInstitution(request.categoryId(), request.institutionId());
        }

        String courseCode = normalize(request.courseCode());
        String courseName = normalize(request.courseName());
        assertUniqueCourseCode(request.institutionId(), courseCode, null);
        assertUniqueCourseName(request.institutionId(), courseName, null);

        CourseEntity entity = new CourseEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCategoryId(request.categoryId());
        entity.setCourseCode(courseCode);
        entity.setCourseName(courseName);
        entity.setSubjectName(normalizeToNull(request.subjectName()));
        entity.setTeachingMethod(normalizeToNull(request.teachingMethod()));
        entity.setStatus(isBlank(request.status()) ? "active" : request.status().trim());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            courseMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Course code or name already exists in institution: " + request.institutionId());
        }
        return entity;
    }

    @Override
    public CourseEntity updateCourse(Long id, CourseUpdateRequest request) {
        CourseEntity existing = requireCourse(id);
        if (request.categoryId() != null && !Objects.equals(existing.getCategoryId(), request.categoryId())) {
            requireCategoryInInstitution(request.categoryId(), existing.getInstitutionId());
            existing.setCategoryId(request.categoryId());
        }
        if (request.courseCode() != null && !request.courseCode().isBlank()) {
            String courseCode = normalize(request.courseCode());
            if (!Objects.equals(existing.getCourseCode(), courseCode)) {
                assertUniqueCourseCode(existing.getInstitutionId(), courseCode, id);
                existing.setCourseCode(courseCode);
            }
        }
        if (request.courseName() != null && !request.courseName().isBlank()) {
            String courseName = normalize(request.courseName());
            if (!Objects.equals(existing.getCourseName(), courseName)) {
                assertUniqueCourseName(existing.getInstitutionId(), courseName, id);
                existing.setCourseName(courseName);
            }
        }
        if (request.subjectName() != null) {
            existing.setSubjectName(normalizeToNull(request.subjectName()));
        }
        if (request.teachingMethod() != null) {
            existing.setTeachingMethod(normalizeToNull(request.teachingMethod()));
        }
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(request.status().trim());
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        try {
            courseMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Course code or name already exists in institution: " + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteCourse(Long id) {
        requireCourse(id);
        courseMapper.deleteById(id);
    }

    private CourseEntity requireCourse(Long id) {
        CourseEntity entity = courseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Course does not exist: " + id);
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + institutionId);
        }
    }

    private void requireCategoryInInstitution(Long categoryId, Long institutionId) {
        CourseCategoryEntity category = courseCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Course category does not exist: " + categoryId);
        }
        if (!Objects.equals(category.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Course category does not belong to institution: " + institutionId);
        }
    }

    private void assertUniqueCourseCode(Long institutionId, String courseCode, Long excludeId) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<CourseEntity>()
            .eq(CourseEntity::getInstitutionId, institutionId)
            .eq(CourseEntity::getCourseCode, courseCode);
        if (excludeId != null) {
            wrapper.ne(CourseEntity::getId, excludeId);
        }
        Long count = courseMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Course code already exists in institution: " + institutionId);
        }
    }

    private void assertUniqueCourseName(Long institutionId, String courseName, Long excludeId) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<CourseEntity>()
            .eq(CourseEntity::getInstitutionId, institutionId)
            .eq(CourseEntity::getCourseName, courseName);
        if (excludeId != null) {
            wrapper.ne(CourseEntity::getId, excludeId);
        }
        Long count = courseMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "Course name already exists in institution: " + institutionId);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
