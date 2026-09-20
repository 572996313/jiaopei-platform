package com.jiaopei.platform.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.education.domain.entity.ClassTypeEntity;
import com.jiaopei.platform.education.domain.entity.ClassStudentEntity;
import com.jiaopei.platform.education.domain.entity.CourseEntity;
import com.jiaopei.platform.education.domain.entity.TeacherEntity;
import com.jiaopei.platform.education.domain.entity.TeachingClassEntity;
import com.jiaopei.platform.education.dto.TeachingClassCreateRequest;
import com.jiaopei.platform.education.mapper.ClassStudentMapper;
import com.jiaopei.platform.education.mapper.ClassTypeMapper;
import com.jiaopei.platform.education.mapper.CourseMapper;
import com.jiaopei.platform.education.mapper.TeacherMapper;
import com.jiaopei.platform.education.mapper.TeachingClassMapper;
import com.jiaopei.platform.education.service.ClassChangeNoticeService;
import com.jiaopei.platform.education.service.TeachingClassService;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.domain.entity.TermEntity;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.tenant.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.mapper.TermMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeachingClassServiceImpl implements TeachingClassService {

    private final TeachingClassMapper teachingClassMapper;
    private final InstitutionMapper institutionMapper;
    private final CampusMapper campusMapper;
    private final TermMapper termMapper;
    private final TeacherMapper teacherMapper;
    private final CourseMapper courseMapper;
    private final ClassTypeMapper classTypeMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassChangeNoticeService classChangeNoticeService;

    public TeachingClassServiceImpl(TeachingClassMapper teachingClassMapper, InstitutionMapper institutionMapper,
                                    CampusMapper campusMapper, TermMapper termMapper, TeacherMapper teacherMapper,
                                    CourseMapper courseMapper, ClassTypeMapper classTypeMapper,
                                    ClassStudentMapper classStudentMapper,
                                    ClassChangeNoticeService classChangeNoticeService) {
        this.teachingClassMapper = teachingClassMapper;
        this.institutionMapper = institutionMapper;
        this.campusMapper = campusMapper;
        this.termMapper = termMapper;
        this.teacherMapper = teacherMapper;
        this.courseMapper = courseMapper;
        this.classTypeMapper = classTypeMapper;
        this.classStudentMapper = classStudentMapper;
        this.classChangeNoticeService = classChangeNoticeService;
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
        validateRequest(request);
        int capacityLimit = normalizeCapacityLimit(request.capacityLimit());
        String classCode = normalize(request.classCode());
        String className = normalize(request.className());
        assertUniqueClassCode(request.campusId(), classCode, null);
        assertUniqueClassName(request.campusId(), className, null);

        TeachingClassEntity entity = new TeachingClassEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setTermId(request.termId());
        entity.setCourseId(request.courseId());
        entity.setClassTypeId(request.classTypeId());
        entity.setClassCode(classCode);
        entity.setClassName(className);
        entity.setTeacherId(request.teacherId());
        entity.setCapacityLimit(capacityLimit);
        entity.setCurrentStudents(0);
        entity.setStatus(normalizeStatus(request.status()));
        entity.setOpenedAt(request.openedAt());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            teachingClassMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下班级编码或班级名称已存在：" + request.campusId());
        }
        classChangeNoticeService.syncClassNotices(entity.getInstitutionId(), entity.getId());
        return entity;
    }

    @Override
    public TeachingClassEntity updateClass(Long id, TeachingClassCreateRequest request) {
        TeachingClassEntity existing = requireClass(id);
        validateRequest(request);
        if (!Objects.equals(existing.getInstitutionId(), request.institutionId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级不属于该机构：" + request.institutionId());
        }
        if (!Objects.equals(existing.getTeacherId(), request.teacherId())) {
            throw new BusinessException(
                ResultCode.BAD_REQUEST,
                "主讲教师只能在班级教师关系中变更，请先在班级教师关系里切换主讲教师"
            );
        }
        int capacityLimit = normalizeCapacityLimit(request.capacityLimit());
        int activeStudents = countActiveStudents(id);
        if (activeStudents > capacityLimit) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "容量上限不能小于当前在班人数：" + activeStudents);
        }
        String classCode = normalize(request.classCode());
        String className = normalize(request.className());
        assertUniqueClassCode(request.campusId(), classCode, id);
        assertUniqueClassName(request.campusId(), className, id);

        existing.setInstitutionId(request.institutionId());
        existing.setCampusId(request.campusId());
        existing.setTermId(request.termId());
        existing.setCourseId(request.courseId());
        existing.setClassTypeId(request.classTypeId());
        existing.setClassCode(classCode);
        existing.setClassName(className);
        existing.setTeacherId(request.teacherId());
        existing.setCapacityLimit(capacityLimit);
        existing.setCurrentStudents(activeStudents);
        existing.setStatus(normalizeStatus(request.status()));
        existing.setOpenedAt(request.openedAt());
        existing.setRemark(request.remark());
        try {
            teachingClassMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下班级编码或班级名称已存在：" + request.campusId());
        }
        classChangeNoticeService.syncClassNotices(existing.getInstitutionId(), existing.getId());
        return existing;
    }

    @Override
    @Transactional
    public void deleteClass(Long id) {
        TeachingClassEntity existing = requireClass(id);
        Long activeStudents = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, id)
            .eq(ClassStudentEntity::getStatus, "active"));
        if (activeStudents != null && activeStudents > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "班级仍有在读学员：" + existing.getId() + "，请先移出班级学员");
        }
        teachingClassMapper.deleteById(id);
    }

    private void validateRequest(TeachingClassCreateRequest request) {
        requireInstitution(request.institutionId());
        requireCampusInInstitution(request.campusId(), request.institutionId());
        if (request.termId() != null) {
            requireTermInInstitution(request.termId(), request.institutionId());
        }
        if (request.courseId() != null) {
            requireCourseInInstitution(request.courseId(), request.institutionId());
        }
        if (request.classTypeId() != null) {
            requireClassTypeInInstitution(request.classTypeId(), request.institutionId());
        }
        if (request.teacherId() != null) {
            requireTeacherInInstitution(request.teacherId(), request.institutionId());
        }
    }

    private void requireInstitution(Long institutionId) {
        if (institutionMapper.selectById(institutionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + institutionId);
        }
    }

    private void requireCampusInInstitution(Long campusId, Long institutionId) {
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + campusId);
        }
        if (!Objects.equals(campus.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "校区不属于该机构：" + institutionId);
        }
    }

    private void requireTermInInstitution(Long termId, Long institutionId) {
        TermEntity term = termMapper.selectById(termId);
        if (term == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学期不存在：" + termId);
        }
        if (!Objects.equals(term.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学期不属于该机构：" + institutionId);
        }
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

    private void requireCourseInInstitution(Long courseId, Long institutionId) {
        CourseEntity course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在：" + courseId);
        }
        if (!Objects.equals(course.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "课程不属于该机构：" + institutionId);
        }
    }

    private void requireClassTypeInInstitution(Long classTypeId, Long institutionId) {
        ClassTypeEntity classType = classTypeMapper.selectById(classTypeId);
        if (classType == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班型不存在：" + classTypeId);
        }
        if (!Objects.equals(classType.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班型不属于该机构：" + institutionId);
        }
    }

    private void assertUniqueClassCode(Long campusId, String classCode, Long excludeId) {
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .eq(TeachingClassEntity::getCampusId, campusId)
            .eq(TeachingClassEntity::getClassCode, classCode);
        if (excludeId != null) {
            wrapper.ne(TeachingClassEntity::getId, excludeId);
        }
        Long count = teachingClassMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下班级编码已存在：" + campusId);
        }
    }

    private void assertUniqueClassName(Long campusId, String className, Long excludeId) {
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .eq(TeachingClassEntity::getCampusId, campusId)
            .eq(TeachingClassEntity::getClassName, className);
        if (excludeId != null) {
            wrapper.ne(TeachingClassEntity::getId, excludeId);
        }
        Long count = teachingClassMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下班级名称已存在：" + campusId);
        }
    }

    private TeachingClassEntity requireClass(Long id) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(id);
        if (teachingClass == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + id);
        }
        return teachingClass;
    }

    private int normalizeCapacityLimit(Integer capacityLimit) {
        return capacityLimit == null ? 0 : capacityLimit;
    }

    private int countActiveStudents(Long classId) {
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStatus, "active"));
        return count == null ? 0 : count.intValue();
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "planning" : status.trim();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
