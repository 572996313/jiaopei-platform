package com.cornerstone.module.education.teachingclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.module.education.domain.entity.ClassChangeNoticeEntity;
import com.cornerstone.module.education.domain.entity.ClassTeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.mapper.ClassChangeNoticeMapper;
import com.cornerstone.module.education.mapper.ClassTeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.teachingclass.service.ClassChangeNoticeService;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ClassChangeNoticeServiceImpl implements ClassChangeNoticeService {

    private final ClassChangeNoticeMapper classChangeNoticeMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final ClassTeacherMapper classTeacherMapper;

    public ClassChangeNoticeServiceImpl(ClassChangeNoticeMapper classChangeNoticeMapper,
                                        TeachingClassMapper teachingClassMapper,
                                        ClassTeacherMapper classTeacherMapper) {
        this.classChangeNoticeMapper = classChangeNoticeMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.classTeacherMapper = classTeacherMapper;
    }

    @Override
    public List<ClassChangeNoticeEntity> listNotices(Long institutionId, Long classId, String noticeStatus) {
        syncClassNotices(institutionId, classId);
        LambdaQueryWrapper<ClassChangeNoticeEntity> wrapper = new LambdaQueryWrapper<ClassChangeNoticeEntity>()
            .orderByDesc(ClassChangeNoticeEntity::getCreatedAt)
            .orderByDesc(ClassChangeNoticeEntity::getId);
        if (institutionId != null) {
            wrapper.eq(ClassChangeNoticeEntity::getInstitutionId, institutionId);
        }
        if (classId != null) {
            wrapper.eq(ClassChangeNoticeEntity::getClassId, classId);
        }
        if (noticeStatus != null && !noticeStatus.isBlank()) {
            wrapper.eq(ClassChangeNoticeEntity::getNoticeStatus, noticeStatus.trim());
        }
        // 通知的校区由班级带出；未带校区的历史行按机构通用处理，受限角色仍可见
        CampusScopeGuard.applyIncludingNull(wrapper, ClassChangeNoticeEntity::getCampusId);
        return classChangeNoticeMapper.selectList(wrapper);
    }

    @Override
    public void syncClassNotices(Long institutionId, Long classId) {
        for (TeachingClassEntity teachingClass : loadScopedClasses(institutionId, classId)) {
            syncProfileNotices(teachingClass);
        }
    }

    @Override
    public void recordPrimaryTeacherChanged(TeachingClassEntity teachingClass, Long sourceId, String noticeMessage,
                                            String suggestedAction) {
        ClassChangeNoticeEntity pendingNotice = findFirstPendingNotice(teachingClass.getId(), "primary_teacher_changed");
        if (pendingNotice == null) {
            createNotice(teachingClass, "class_teacher", sourceId, "primary_teacher_changed", "info",
                noticeMessage, suggestedAction);
            return;
        }
        pendingNotice.setCampusId(teachingClass.getCampusId());
        pendingNotice.setSourceType("class_teacher");
        pendingNotice.setSourceId(sourceId);
        pendingNotice.setNoticeLevel("info");
        pendingNotice.setNoticeMessage(noticeMessage);
        pendingNotice.setSuggestedAction(suggestedAction);
        pendingNotice.setNoticeStatus("pending");
        pendingNotice.setResolvedAt(null);
        pendingNotice.setResolvedBy(null);
        classChangeNoticeMapper.updateById(pendingNotice);
        resolveDuplicatePendingNotices(teachingClass.getId(), "primary_teacher_changed", pendingNotice.getId());
    }

    private List<TeachingClassEntity> loadScopedClasses(Long institutionId, Long classId) {
        LambdaQueryWrapper<TeachingClassEntity> wrapper = new LambdaQueryWrapper<TeachingClassEntity>()
            .orderByAsc(TeachingClassEntity::getId);
        if (classId != null) {
            wrapper.eq(TeachingClassEntity::getId, classId);
        } else if (institutionId != null) {
            wrapper.eq(TeachingClassEntity::getInstitutionId, institutionId);
        } else {
            return List.of();
        }
        // 同步会写通知行，必须先按可访问校区收窄班级，避免受限角色借同步接口触达其他校区
        CampusScopeGuard.apply(wrapper, TeachingClassEntity::getCampusId);
        return teachingClassMapper.selectList(wrapper);
    }

    private void syncProfileNotices(TeachingClassEntity teachingClass) {
        upsertStateNotice(
            teachingClass,
            "missing_course",
            teachingClass.getCourseId() == null,
            "warning",
            "班级缺少课程绑定，后续课次执行与统计口径会受影响。",
            "请先补齐班级课程，再继续排课与执行。"
        );
        upsertStateNotice(
            teachingClass,
            "missing_class_type",
            teachingClass.getClassTypeId() == null,
            "warning",
            "班级缺少班型信息，成班规则与容量口径暂时无法准确承接。",
            "请尽快为班级补齐班型。"
        );
        upsertStateNotice(
            teachingClass,
            "missing_primary_teacher",
            !hasPrimaryTeacher(teachingClass),
            "warning",
            "班级当前缺少主讲教师，排课和责任归属存在风险。",
            "请尽快指定一位有效主讲教师。"
        );
    }

    private boolean hasPrimaryTeacher(TeachingClassEntity teachingClass) {
        if (teachingClass.getTeacherId() != null) {
            return true;
        }
        LocalDate today = LocalDate.now();
        ClassTeacherEntity primaryTeacher = classTeacherMapper.selectOne(new LambdaQueryWrapper<ClassTeacherEntity>()
            .eq(ClassTeacherEntity::getClassId, teachingClass.getId())
            .eq(ClassTeacherEntity::getIsPrimary, Boolean.TRUE)
            .eq(ClassTeacherEntity::getStatus, "active")
            .and(wrapper -> wrapper.isNull(ClassTeacherEntity::getLeftAt)
                .or()
                .ge(ClassTeacherEntity::getLeftAt, today))
            .last("LIMIT 1"));
        return primaryTeacher != null;
    }

    private void upsertStateNotice(TeachingClassEntity teachingClass, String noticeType, boolean shouldExist,
                                   String noticeLevel, String noticeMessage, String suggestedAction) {
        List<ClassChangeNoticeEntity> pendingNotices = findPendingNotices(teachingClass.getId(), noticeType);
        if (!shouldExist) {
            resolvePendingNotices(pendingNotices);
            return;
        }

        ClassChangeNoticeEntity currentNotice = pendingNotices.isEmpty() ? null : pendingNotices.get(0);
        if (currentNotice == null) {
            createNotice(teachingClass, "class_profile", teachingClass.getId(), noticeType, noticeLevel,
                noticeMessage, suggestedAction);
            return;
        }

        if (!Objects.equals(currentNotice.getCampusId(), teachingClass.getCampusId())
            || !Objects.equals(currentNotice.getSourceId(), teachingClass.getId())
            || !Objects.equals(currentNotice.getNoticeLevel(), noticeLevel)
            || !Objects.equals(currentNotice.getNoticeMessage(), noticeMessage)
            || !Objects.equals(currentNotice.getSuggestedAction(), suggestedAction)
            || !Objects.equals(currentNotice.getNoticeStatus(), "pending")) {
            currentNotice.setCampusId(teachingClass.getCampusId());
            currentNotice.setSourceType("class_profile");
            currentNotice.setSourceId(teachingClass.getId());
            currentNotice.setNoticeLevel(noticeLevel);
            currentNotice.setNoticeMessage(noticeMessage);
            currentNotice.setSuggestedAction(suggestedAction);
            currentNotice.setNoticeStatus("pending");
            currentNotice.setResolvedAt(null);
            currentNotice.setResolvedBy(null);
            classChangeNoticeMapper.updateById(currentNotice);
        }
        resolveDuplicatePendingNotices(teachingClass.getId(), noticeType, currentNotice.getId());
    }

    private List<ClassChangeNoticeEntity> findPendingNotices(Long classId, String noticeType) {
        if (classId == null) {
            return List.of();
        }
        return new ArrayList<>(classChangeNoticeMapper.selectList(new LambdaQueryWrapper<ClassChangeNoticeEntity>()
            .eq(ClassChangeNoticeEntity::getClassId, classId)
            .eq(ClassChangeNoticeEntity::getNoticeType, noticeType)
            .eq(ClassChangeNoticeEntity::getNoticeStatus, "pending")
            .orderByAsc(ClassChangeNoticeEntity::getCreatedAt)
            .orderByAsc(ClassChangeNoticeEntity::getId)));
    }

    private ClassChangeNoticeEntity findFirstPendingNotice(Long classId, String noticeType) {
        List<ClassChangeNoticeEntity> notices = findPendingNotices(classId, noticeType);
        return notices.isEmpty() ? null : notices.get(0);
    }

    private void resolvePendingNotices(List<ClassChangeNoticeEntity> pendingNotices) {
        LocalDateTime resolvedAt = LocalDateTime.now();
        for (ClassChangeNoticeEntity pendingNotice : pendingNotices) {
            pendingNotice.setNoticeStatus("resolved");
            pendingNotice.setResolvedAt(resolvedAt);
            classChangeNoticeMapper.updateById(pendingNotice);
        }
    }

    private void resolveDuplicatePendingNotices(Long classId, String noticeType, Long keepId) {
        LocalDateTime resolvedAt = LocalDateTime.now();
        for (ClassChangeNoticeEntity pendingNotice : findPendingNotices(classId, noticeType)) {
            if (Objects.equals(pendingNotice.getId(), keepId)) {
                continue;
            }
            pendingNotice.setNoticeStatus("resolved");
            pendingNotice.setResolvedAt(resolvedAt);
            classChangeNoticeMapper.updateById(pendingNotice);
        }
    }

    private void createNotice(TeachingClassEntity teachingClass, String sourceType, Long sourceId, String noticeType,
                              String noticeLevel, String noticeMessage, String suggestedAction) {
        ClassChangeNoticeEntity entity = new ClassChangeNoticeEntity();
        entity.setInstitutionId(teachingClass.getInstitutionId());
        entity.setCampusId(teachingClass.getCampusId());
        entity.setClassId(teachingClass.getId());
        entity.setSourceType(sourceType);
        entity.setSourceId(sourceId);
        entity.setNoticeType(noticeType);
        entity.setNoticeLevel(noticeLevel);
        entity.setNoticeStatus("pending");
        entity.setNoticeMessage(noticeMessage);
        entity.setSuggestedAction(suggestedAction);
        entity.setDeleted(Boolean.FALSE);
        classChangeNoticeMapper.insert(entity);
    }
}
