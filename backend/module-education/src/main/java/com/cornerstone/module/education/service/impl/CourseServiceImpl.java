package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.module.education.domain.entity.CourseCategoryEntity;
import com.cornerstone.module.education.domain.entity.CourseEntity;
import com.cornerstone.module.education.dto.CourseCreateRequest;
import com.cornerstone.module.education.dto.CoursePageQuery;
import com.cornerstone.module.education.dto.CourseUpdateRequest;
import com.cornerstone.module.education.mapper.CourseCategoryMapper;
import com.cornerstone.module.education.mapper.CourseMapper;
import com.cornerstone.module.education.service.CourseService;
import com.cornerstone.module.education.service.support.DictValueValidator;
import com.cornerstone.core.tenant.mapper.InstitutionMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class CourseServiceImpl implements CourseService {

    /** 课程列表可排序字段 */
    private static final Map<String, SFunction<CourseEntity, ?>> COURSE_SORTABLE = Map.of(
        "courseCode", CourseEntity::getCourseCode,
        "courseName", CourseEntity::getCourseName,
        "subjectName", CourseEntity::getSubjectName,
        "teachingMethod", CourseEntity::getTeachingMethod,
        "status", CourseEntity::getStatus);

    private final CourseMapper courseMapper;
    private final CourseCategoryMapper courseCategoryMapper;
    private final InstitutionMapper institutionMapper;
    private final DictValueValidator dictValueValidator;

    public CourseServiceImpl(CourseMapper courseMapper, CourseCategoryMapper courseCategoryMapper,
                             InstitutionMapper institutionMapper, DictValueValidator dictValueValidator) {
        this.courseMapper = courseMapper;
        this.courseCategoryMapper = courseCategoryMapper;
        this.institutionMapper = institutionMapper;
        this.dictValueValidator = dictValueValidator;
    }

    @Override
    public List<CourseEntity> listCourses(Long institutionId, Long categoryId) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<>();
        if (institutionId != null) {
            wrapper.eq(CourseEntity::getInstitutionId, institutionId);
        }
        if (categoryId != null) {
            wrapper.eq(CourseEntity::getCategoryId, categoryId);
        }
        applyDefaultOrder(wrapper);
        return courseMapper.selectList(wrapper);
    }

    @Override
    public PageResult<CourseEntity> pageCourses(CoursePageQuery query) {
        LambdaQueryWrapper<CourseEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getInstitutionId() != null) {
            wrapper.eq(CourseEntity::getInstitutionId, query.getInstitutionId());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(CourseEntity::getCategoryId, query.getCategoryId());
        }
        String keyword = normalize(query.getKeyword());
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                .like(CourseEntity::getCourseName, keyword)
                .or()
                .like(CourseEntity::getCourseCode, keyword)
                .or()
                .like(CourseEntity::getSubjectName, keyword));
        }
        String status = normalize(query.getStatus());
        if (status != null && !status.isBlank()) {
            wrapper.eq(CourseEntity::getStatus, status);
        }
        if (!PageSortSupport.apply(wrapper, query, COURSE_SORTABLE, CourseEntity::getId)) {
            applyDefaultOrder(wrapper);
        }
        Page<CourseEntity> page = courseMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords());
    }

    /** 列表与分页共用默认排序 */
    private void applyDefaultOrder(LambdaQueryWrapper<CourseEntity> wrapper) {
        wrapper.orderByAsc(CourseEntity::getInstitutionId)
            .orderByAsc(CourseEntity::getCategoryId)
            .orderByAsc(CourseEntity::getCourseCode);
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
        entity.setTeachingMethod(dictValueValidator.validate("teaching_method", "授课方式", request.teachingMethod()));
        entity.setStatus(isBlank(request.status()) ? "active" : request.status().trim());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            courseMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该机构下课程编码或名称已存在：" + request.institutionId());
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
            existing.setTeachingMethod(dictValueValidator.validate("teaching_method", "授课方式", request.teachingMethod()));
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
                "该机构下课程编码或名称已存在：" + existing.getInstitutionId());
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
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在：" + id);
        }
        return entity;
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
    }

    private void requireCategoryInInstitution(Long categoryId, Long institutionId) {
        CourseCategoryEntity category = courseCategoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程分类不存在：" + categoryId);
        }
        if (!Objects.equals(category.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "课程分类不属于该机构：" + institutionId);
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
                "该机构下课程编码已存在：" + institutionId);
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
                "该机构下课程名称已存在：" + institutionId);
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
