package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.ScheduleAutoBatchEntity;
import com.cornerstone.module.education.domain.entity.ScheduleLessonEntity;
import com.cornerstone.module.education.domain.entity.ScheduleTeacherRelEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ScheduleAutoBatchRequest;
import com.cornerstone.module.education.dto.ScheduleAutoBatchView;
import com.cornerstone.module.education.dto.ScheduleAutoFailure;
import com.cornerstone.module.education.dto.ScheduleAutoPlacement;
import com.cornerstone.module.education.dto.ScheduleAutoPlanResponse;
import com.cornerstone.module.education.dto.ScheduleAutoRollbackResponse;
import com.cornerstone.module.education.dto.ScheduleAutoStrategy;
import com.cornerstone.module.education.dto.ScheduleCompletenessQuery;
import com.cornerstone.module.education.dto.ScheduleCompletenessResponse;
import com.cornerstone.module.education.mapper.ScheduleAutoBatchMapper;
import com.cornerstone.module.education.mapper.ScheduleLessonMapper;
import com.cornerstone.module.education.mapper.ScheduleTeacherRelMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ScheduleAutoBatchService;
import com.cornerstone.module.education.service.ScheduleCompletenessService;
import com.cornerstone.module.education.service.support.ScheduleGapPlanner;
import com.cornerstone.module.education.service.support.ScheduleInstitutionGuard;
import com.cornerstone.module.education.service.support.ScheduleTeacherLock;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 缺口补排的编排：校验范围 → 取完成度报表 → 交给 {@link ScheduleGapPlanner} 决策 → 按同一份结果落库。
 *
 * <p>同步执行、不起异步任务也不加"批次进行中"的锁：一次请求就是一次事务，跑完即完。重复点的幂等靠
 * 报表本身——上一批排上的课次会填掉缺口，下一次进引擎的只剩真缺口。</p>
 */
@Slf4j
@Service
public class ScheduleAutoBatchServiceImpl implements ScheduleAutoBatchService {

    private static final String SUBJECT = "自动排课批次";
    private static final String STATUS_ACTIVE = "active";
    private static final String BATCH_COMPLETED = "completed";
    private static final String BATCH_ROLLED_BACK = "rolled_back";
    /** 与计划展开器同口径：只有这三种状态占住格子，已停课的课次可以让位给补课。 */
    private static final List<String> OCCUPYING_STATUSES = List.of("scheduled", "rescheduled", "makeup");
    /** 批次清单不翻页：它是"刚才那几次补排在干什么"的回溯入口，超过一屏说明没人会看。 */
    private static final int BATCH_LIST_LIMIT = 20;

    private final ScheduleAutoBatchMapper batchMapper;
    private final TeachingClassMapper classMapper;
    private final CampusMapper campusMapper;
    private final TermMapper termMapper;
    private final ScheduleLessonMapper lessonMapper;
    private final ScheduleTeacherRelMapper teacherRelMapper;
    private final ScheduleCompletenessService completenessService;
    private final ScheduleGapPlanner planner;
    private final ScheduleTeacherLock teacherLock;
    private final ObjectMapper objectMapper;

    public ScheduleAutoBatchServiceImpl(ScheduleAutoBatchMapper batchMapper, TeachingClassMapper classMapper,
                                        CampusMapper campusMapper, TermMapper termMapper,
                                        ScheduleLessonMapper lessonMapper,
                                        ScheduleTeacherRelMapper teacherRelMapper,
                                        ScheduleCompletenessService completenessService,
                                        ScheduleGapPlanner planner, ScheduleTeacherLock teacherLock,
                                        ObjectMapper objectMapper) {
        this.batchMapper = batchMapper;
        this.classMapper = classMapper;
        this.campusMapper = campusMapper;
        this.termMapper = termMapper;
        this.lessonMapper = lessonMapper;
        this.teacherRelMapper = teacherRelMapper;
        this.completenessService = completenessService;
        this.planner = planner;
        this.teacherLock = teacherLock;
        this.objectMapper = objectMapper;
    }

    @Override
    public ScheduleAutoPlanResponse preview(ScheduleAutoBatchRequest request) {
        Scoped scoped = prepare(request);
        ScheduleGapPlanner.Result result = planner.plan(scoped.institutionId(), scoped.campusId(),
            scoped.startDate(), scoped.endDate(), scoped.classIds(), scoped.strategy(), scoped.report());
        return new ScheduleAutoPlanResponse(false, null, scoped.startDate(), scoped.endDate(),
            result.requestedCount(), result.placements().size(), unscheduled(result),
            result.placements(), result.failures());
    }

    @Override
    @Transactional
    public ScheduleAutoPlanResponse run(ScheduleAutoBatchRequest request) {
        Scoped scoped = prepare(request);
        ScheduleGapPlanner.Result result = planner.plan(scoped.institutionId(), scoped.campusId(),
            scoped.startDate(), scoped.endDate(), scoped.classIds(), scoped.strategy(), scoped.report());
        if (result.placements().isEmpty()) {
            // 一节都没排上就不留批次行：清单里堆一串 generated=0 的批次，只会让人以为"排过了但没生效"
            return new ScheduleAutoPlanResponse(true, null, scoped.startDate(), scoped.endDate(),
                result.requestedCount(), 0, unscheduled(result), List.of(), result.failures());
        }

        ScheduleAutoBatchEntity batch = new ScheduleAutoBatchEntity();
        batch.setInstitutionId(scoped.institutionId());
        batch.setCampusId(scoped.campusId());
        batch.setTermId(request.termId());
        batch.setStartDate(scoped.startDate());
        batch.setEndDate(scoped.endDate());
        batch.setClassIdsJson(writeJson(scoped.classIds()));
        batch.setStrategyJson(writeJson(scoped.strategy()));
        batch.setRequestedCount(result.requestedCount());
        batch.setGeneratedCount(result.placements().size());
        batch.setUnscheduledCount(unscheduled(result));
        batch.setFailureJson(writeJson(result.failures()));
        batch.setStatus(BATCH_COMPLETED);
        batch.setRemark(normalize(request.remark()));
        batch.setDeleted(Boolean.FALSE);
        batchMapper.insert(batch);

        // 教师占用没有唯一键可兜（占用跨课次表与教师关系表），先查后插之前取行锁；乱序会死锁，锁内部按 ID 升序
        teacherLock.lock(teacherIdsOf(result.placements()));
        Map<Long, Long> termIdByClass = termIdByClass(result.placements());
        for (ScheduleAutoPlacement placement : result.placements()) {
            insertLesson(scoped, batch.getId(), placement, termIdByClass);
        }
        log.info("自动补排批次 {} 生成 {} 节课次，未排上 {} 条，窗口 {} ~ {}", batch.getId(),
            result.placements().size(), result.failures().size(), scoped.startDate(), scoped.endDate());
        return new ScheduleAutoPlanResponse(true, batch.getId(), scoped.startDate(), scoped.endDate(),
            result.requestedCount(), result.placements().size(), unscheduled(result),
            result.placements(), result.failures());
    }

    /** 没补上的节数用"缺口减到位"算，不数失败条数：某一周排不下去就整周停止尝试，一条原因背后可能压着好几节。 */
    private int unscheduled(ScheduleGapPlanner.Result result) {
        return result.requestedCount() - result.placements().size();
    }

    /** 撤销结果要说给操作员看，状态码不能裸着出现；字典外的值原样回显，别硬编一个"未知"。 */
    private String lessonStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case "scheduled" -> "已排课";
            case "rescheduled" -> "已调课";
            case "cancelled" -> "已停课";
            case "makeup" -> "已补课";
            case "completed" -> "已结课";
            default -> status;
        };
    }

    @Override
    public List<ScheduleAutoBatchView> listBatches(Long institutionId, Long campusId) {
        Long scopedInstitution = ScheduleInstitutionGuard.resolveForQuery(institutionId, SUBJECT);
        LambdaQueryWrapper<ScheduleAutoBatchEntity> wrapper = new LambdaQueryWrapper<ScheduleAutoBatchEntity>()
            .eq(ScheduleAutoBatchEntity::getInstitutionId, scopedInstitution)
            .orderByDesc(ScheduleAutoBatchEntity::getCreatedAt)
            .last("LIMIT " + BATCH_LIST_LIMIT);
        if (campusId != null) {
            wrapper.eq(ScheduleAutoBatchEntity::getCampusId, campusId);
        }
        return batchMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    @Override
    @Transactional
    public ScheduleAutoRollbackResponse rollback(Long id) {
        ScheduleAutoBatchEntity batch = requireBatch(id);
        if (BATCH_ROLLED_BACK.equals(batch.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该批次已撤销过，无需重复操作");
        }
        List<ScheduleLessonEntity> lessons = lessonMapper.selectList(
            new LambdaQueryWrapper<ScheduleLessonEntity>().eq(ScheduleLessonEntity::getAutoBatchId, id));
        LocalDate today = LocalDate.now();
        int canceled = 0;
        List<String> keptDescriptions = new ArrayList<>();
        for (ScheduleLessonEntity lesson : lessons) {
            if (!OCCUPYING_STATUSES.contains(lesson.getLessonStatus()) || lesson.getLessonDate().isBefore(today)) {
                keptDescriptions.add(lesson.getLessonDate() + " " + lessonStatusLabel(lesson.getLessonStatus()));
                continue;
            }
            // 与「撤销计划课次」同一套：授课关系物理删，课次行留给 @TableLogic 打标记
            teacherRelMapper.physicalDeleteByLessonId(lesson.getId());
            lessonMapper.deleteById(lesson.getId());
            canceled++;
        }
        batch.setStatus(BATCH_ROLLED_BACK);
        batch.setRolledBackAt(LocalDateTime.now());
        batchMapper.updateById(batch);

        int kept = lessons.size() - canceled;
        String message = kept == 0
            ? "已撤销该批次生成的 " + canceled + " 节课次"
            : "已撤销 " + canceled + " 节课次；另有 " + kept + " 节已开课或已过上课日（"
                + String.join("、", keptDescriptions) + "），未被撤销，需要停请单独走「停课」";
        return new ScheduleAutoRollbackResponse(id, batch.getGeneratedCount(), canceled, kept, message);
    }

    private ScheduleAutoBatchEntity requireBatch(Long id) {
        ScheduleAutoBatchEntity entity = batchMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "自动排课批次不存在：" + id);
        }
        ScheduleInstitutionGuard.assertOwn(entity.getInstitutionId(), SUBJECT);
        return entity;
    }

    /**
     * 校验范围并取报表：预览与执行都走这一关，两边算出的缺口必须来自同一份数据。
     *
     * <p>机构比对在 {@link ScheduleInstitutionGuard} 里——客户端传来的 institutionId 只是声明，
     * 不跟登录机构核就等于把别人的班级排进自己的课表。学期/校区/班级同理逐个认归属。</p>
     */
    private Scoped prepare(ScheduleAutoBatchRequest request) {
        ScheduleInstitutionGuard.assertOwn(request.institutionId(), SUBJECT);
        CampusEntity campus = campusMapper.selectById(request.campusId());
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + request.campusId());
        }
        if (!Objects.equals(campus.getInstitutionId(), request.institutionId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "校区不属于本机构：" + campus.getCampusName());
        }
        if (!STATUS_ACTIVE.equalsIgnoreCase(normalize(campus.getStatus()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区已停用，不能自动排课：" + campus.getCampusName());
        }
        TermEntity term = termMapper.selectById(request.termId());
        if (term == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学期不存在：" + request.termId());
        }
        if (!Objects.equals(term.getInstitutionId(), request.institutionId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学期不属于本机构");
        }
        LocalDate from = request.startDate();
        LocalDate to = request.endDate();
        if (from == null || to == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "补排的起止日期都不能为空");
        }
        if (to.isBefore(from)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "截止日期不能早于起始日期");
        }
        if (from.isBefore(LocalDate.now())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "起始日期不能早于今天（" + LocalDate.now() + "）：补排只往还没上的课里填，历史课次请手工调整");
        }
        if (term.getStartDate() != null && to.isBefore(term.getStartDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "补排窗口在学期「" + term.getTermName() + "」开始之前，请调整日期");
        }
        if (term.getEndDate() != null && from.isAfter(term.getEndDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "补排窗口在学期「" + term.getTermName() + "」结束之后，请调整日期");
        }
        List<Long> classIds = request.classIds() == null ? List.of()
            : request.classIds().stream().filter(Objects::nonNull).distinct().toList();
        for (Long classId : classIds) {
            TeachingClassEntity teachingClass = classMapper.selectById(classId);
            if (teachingClass == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + classId);
            }
            if (!Objects.equals(teachingClass.getInstitutionId(), request.institutionId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "班级不属于本机构：" + teachingClass.getClassName());
            }
            if (!Objects.equals(teachingClass.getCampusId(), request.campusId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "班级「" + teachingClass.getClassName() + "」不在所选校区「" + campus.getCampusName() + "」");
            }
        }
        ScheduleCompletenessQuery query = new ScheduleCompletenessQuery();
        query.setInstitutionId(request.institutionId());
        query.setCampusId(request.campusId());
        query.setTermId(request.termId());
        query.setDateFrom(from);
        query.setDateTo(to);
        return new Scoped(request.institutionId(), request.campusId(), request.termId(), from, to, classIds,
            request.strategy() == null ? new ScheduleAutoStrategy(null, null, null) : request.strategy(),
            completenessService.report(request.institutionId(), query));
    }

    private void insertLesson(Scoped scoped, Long batchId, ScheduleAutoPlacement placement,
                              Map<Long, Long> termIdByClass) {
        ScheduleLessonEntity lesson = new ScheduleLessonEntity();
        lesson.setInstitutionId(scoped.institutionId());
        lesson.setCampusId(scoped.campusId());
        lesson.setTermId(termIdByClass.getOrDefault(placement.classId(), scoped.termId()));
        lesson.setClassId(placement.classId());
        lesson.setClassroomId(placement.classroomId());
        lesson.setTimeSlotId(placement.timeSlotId());
        // plan_id 留空：缺口格子虽然来自某份计划，报表的缺口条目并不带计划 ID，猜一个反而会让「撤销计划课次」误删
        lesson.setAutoBatchId(batchId);
        lesson.setLessonDate(placement.lessonDate());
        lesson.setLessonWeekday(placement.weekday());
        lesson.setStartTime(placement.startTime());
        lesson.setEndTime(placement.endTime());
        lesson.setLessonStatus("scheduled");
        lesson.setDeleted(Boolean.FALSE);
        try {
            lessonMapper.insert(lesson);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该班级或教室在此时段已有课次（并发写入被数据库唯一约束拦下）："
                    + placement.lessonDate() + " " + placement.slotName() + "，本次补排未生效");
        }
        for (Long teacherId : placement.teacherIds()) {
            ScheduleTeacherRelEntity relation = new ScheduleTeacherRelEntity();
            relation.setLessonId(lesson.getId());
            relation.setTeacherId(teacherId);
            relation.setDeleted(Boolean.FALSE);
            teacherRelMapper.insert(relation);
        }
    }

    /** 课次的学期跟班级自己的学期，不是批次选的那个学期：班级在 A 学期、窗口落在 B 学期时不该把课挂到 B。一次取齐，别逐条查库。 */
    private Map<Long, Long> termIdByClass(List<ScheduleAutoPlacement> placements) {
        List<Long> classIds = placements.stream().map(ScheduleAutoPlacement::classId).distinct().toList();
        Map<Long, Long> termIds = new HashMap<>();
        for (TeachingClassEntity teachingClass : classMapper.selectBatchIds(classIds)) {
            if (teachingClass.getTermId() != null) {
                termIds.put(teachingClass.getId(), teachingClass.getTermId());
            }
        }
        return termIds;
    }

    private List<Long> teacherIdsOf(List<ScheduleAutoPlacement> placements) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ScheduleAutoPlacement placement : placements) {
            ids.addAll(placement.teacherIds());
        }
        return List.copyOf(ids);
    }

    private ScheduleAutoBatchView toView(ScheduleAutoBatchEntity entity) {
        return new ScheduleAutoBatchView(entity.getId(), entity.getInstitutionId(), entity.getCampusId(),
            entity.getTermId(), entity.getStartDate(), entity.getEndDate(),
            readJson(entity.getClassIdsJson(), new TypeReference<List<Long>>() {
            }),
            readJson(entity.getStrategyJson(), new TypeReference<ScheduleAutoStrategy>() {
            }),
            entity.getRequestedCount(), entity.getGeneratedCount(), entity.getUnscheduledCount(),
            readJson(entity.getFailureJson(), new TypeReference<List<ScheduleAutoFailure>>() {
            }),
            entity.getStatus(), entity.getRolledBackAt(), entity.getRemark(), entity.getCreatedAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "自动排课批次数据序列化失败：" + exception.getMessage());
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            // 读不出策略/明细只影响清单展示，不该让整页打不开
            log.warn("自动排课批次的 JSON 字段解析失败，按空处理：{}", exception.getMessage());
            return null;
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private record Scoped(Long institutionId, Long campusId, Long termId, LocalDate startDate, LocalDate endDate,
                          List<Long> classIds, ScheduleAutoStrategy strategy, ScheduleCompletenessResponse report) {
    }
}
