package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ClassStudentCreateRequest;
import com.cornerstone.module.education.dto.ClassStudentPageQuery;
import com.cornerstone.module.education.dto.ClassStudentView;
import com.cornerstone.module.education.mapper.ClassStudentMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ClassStudentService;
import com.cornerstone.module.education.service.support.EducationCampusScopeSupport;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import com.cornerstone.module.organization.service.support.InstitutionScopeGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassStudentServiceImpl implements ClassStudentService {

    /** 班级学员列表可排序字段:班级/学员是 ID 列,列表上显示的是名称,不参与排序 */
    private static final Map<String, SFunction<ClassStudentEntity, ?>> CLASS_STUDENT_SORTABLE = Map.of(
        "status", ClassStudentEntity::getStatus,
        "joinedAt", ClassStudentEntity::getJoinedAt,
        "leftAt", ClassStudentEntity::getLeftAt,
        "remark", ClassStudentEntity::getRemark);

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
        EducationCampusScopeSupport.applyClassVisible(wrapper, ClassStudentEntity::getClassId);
        return classStudentMapper.selectList(wrapper);
    }

    @Override
    public PageResult<ClassStudentView> pageClassStudents(ClassStudentPageQuery query) {
        Long resolvedInstitutionId = InstitutionScopeGuard.resolveForQuery(query.getInstitutionId(), "班级学员");
        LambdaQueryWrapper<ClassStudentEntity> wrapper = new LambdaQueryWrapper<>();
        if (resolvedInstitutionId != null) {
            wrapper.eq(ClassStudentEntity::getInstitutionId, resolvedInstitutionId);
        }
        if (query.getClassId() != null) {
            wrapper.eq(ClassStudentEntity::getClassId, query.getClassId());
        }
        if (query.getStudentId() != null) {
            wrapper.eq(ClassStudentEntity::getStudentId, query.getStudentId());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(ClassStudentEntity::getStatus, query.getStatus().trim());
        }
        if (query.getCampusId() != null) {
            CampusScopeGuard.assertCampus(query.getCampusId(), "班级学员");
            // 班级学员关系表没有校区列,只能先按校区取班级 ID 再限定
            List<Long> campusClassIds = teachingClassMapper.selectList(new LambdaQueryWrapper<TeachingClassEntity>()
                    .select(TeachingClassEntity::getId)
                    .eq(resolvedInstitutionId != null, TeachingClassEntity::getInstitutionId, resolvedInstitutionId)
                    .eq(TeachingClassEntity::getCampusId, query.getCampusId()))
                .stream()
                .map(TeachingClassEntity::getId)
                .toList();
            if (campusClassIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, query.getPage(), query.getPageSize());
            }
            wrapper.in(ClassStudentEntity::getClassId, campusClassIds);
        }
        // 客户端 campusId 只是额外筛选；受限角色即使不传也要按可访问班级收窄
        EducationCampusScopeSupport.applyClassVisible(wrapper, ClassStudentEntity::getClassId);
        if (!PageSortSupport.apply(wrapper, query, CLASS_STUDENT_SORTABLE, ClassStudentEntity::getId)) {
            wrapper.orderByAsc(ClassStudentEntity::getClassId).orderByAsc(ClassStudentEntity::getStudentId);
        }
        Page<ClassStudentEntity> page = classStudentMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, toViews(page.getRecords()));
    }

    /** 批量回带班级与学员名称,前端分页列表无需再全量加载班级/学员做名称回显 */
    private List<ClassStudentView> toViews(List<ClassStudentEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<Long, TeachingClassEntity> classMap = teachingClassMapper.selectBatchIds(entities.stream()
                .map(ClassStudentEntity::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .stream()
            .collect(Collectors.toMap(TeachingClassEntity::getId, Function.identity()));
        Map<Long, StudentEntity> studentMap = studentMapper.selectBatchIds(entities.stream()
                .map(ClassStudentEntity::getStudentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
            .stream()
            .collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        return entities.stream()
            .map(entity -> {
                TeachingClassEntity teachingClass = classMap.get(entity.getClassId());
                StudentEntity student = studentMap.get(entity.getStudentId());
                return new ClassStudentView(
                    entity.getId(),
                    entity.getInstitutionId(),
                    entity.getClassId(),
                    teachingClass == null ? null : teachingClass.getClassCode(),
                    teachingClass == null ? null : teachingClass.getClassName(),
                    teachingClass == null ? null : teachingClass.getCourseId(),
                    teachingClass == null ? null : teachingClass.getClassTypeId(),
                    entity.getStudentId(),
                    student == null ? null : student.getStudentNo(),
                    student == null ? null : student.getStudentName(),
                    entity.getStatus(),
                    entity.getJoinedAt(),
                    entity.getLeftAt(),
                    entity.getRemark()
                );
            })
            .toList();
    }

    @Override
    public int countActiveStudents(Long institutionId, Long classId) {
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(institutionId != null, ClassStudentEntity::getInstitutionId, institutionId)
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStatus, "active"));
        return count == null ? 0 : count.intValue();
    }

    @Override
    @Transactional
    public ClassStudentEntity createClassStudent(ClassStudentCreateRequest request) {
        TeachingClassEntity teachingClass = requireClass(request.classId());
        CampusScopeGuard.assertCampus(teachingClass.getCampusId(), "班级学员");
        StudentEntity student = requireStudent(request.studentId());
        Long institutionId = request.institutionId() == null ? teachingClass.getInstitutionId() : request.institutionId();
        if (!Objects.equals(teachingClass.getInstitutionId(), institutionId) || !Objects.equals(student.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "班级「" + teachingClass.getClassCode() + "」与学员「" + student.getStudentNo() + "」必须属于同一机构");
        }
        if (request.leftAt() != null && request.joinedAt() != null && request.leftAt().isBefore(request.joinedAt())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "离开日期不能早于加入日期");
        }
        assertUniqueClassStudent(request.classId(), request.studentId(), teachingClass.getClassCode());
        int activeStudents = countActiveStudents(institutionId, request.classId());
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该学员已加入此班级：" + teachingClass.getClassCode());
        }

        // 当前人数按在班学员关系重算，只统计 status=active 的记录，并顺带修复历史脏数据
        teachingClass.setCurrentStudents(countActiveStudents(institutionId, request.classId()));
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
        TeachingClassEntity owningClass = requireClass(existing.getClassId());
        CampusScopeGuard.assertCampus(owningClass.getCampusId(), "班级学员");
        classStudentMapper.deletePhysicallyById(id);
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(existing.getClassId());
        if (teachingClass != null) {
            teachingClass.setCurrentStudents(countActiveStudents(existing.getInstitutionId(), existing.getClassId()));
            teachingClassMapper.updateById(teachingClass);
        }
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

    private void assertUniqueClassStudent(Long classId, Long studentId, String classCode) {
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudentEntity>()
            .eq(ClassStudentEntity::getClassId, classId)
            .eq(ClassStudentEntity::getStudentId, studentId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该学员已加入此班级：" + classCode);
        }
    }

    private void assertClassCapacity(TeachingClassEntity teachingClass, int activeStudents) {
        int capacity = teachingClass.getCapacityLimit() == null ? 0 : teachingClass.getCapacityLimit();
        if (capacity > 0 && activeStudents >= capacity) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级容量已满：" + teachingClass.getClassCode());
        }
    }
}
