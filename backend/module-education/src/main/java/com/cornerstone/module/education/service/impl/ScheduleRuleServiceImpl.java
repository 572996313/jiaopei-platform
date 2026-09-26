package com.cornerstone.module.education.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.module.education.domain.entity.ScheduleRuleEntity;
import com.cornerstone.module.education.domain.entity.TeacherEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.dto.ScheduleRulePageQuery;
import com.cornerstone.module.education.dto.ScheduleRuleRequest;
import com.cornerstone.module.education.dto.ScheduleRuleView;
import com.cornerstone.module.education.dto.ScheduleSlotCell;
import com.cornerstone.module.education.mapper.ScheduleRuleMapper;
import com.cornerstone.module.education.mapper.TeacherMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.education.service.ScheduleRuleService;
import com.cornerstone.module.education.service.support.ScheduleInstitutionGuard;
import com.cornerstone.module.education.service.support.ScheduleRuleSupport;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleRuleServiceImpl implements ScheduleRuleService {

    /** 冲突规则列表可排序字段:生效对象是 ID 列,列表上显示的是名称,不参与排序 */
    private static final Map<String, SFunction<ScheduleRuleEntity, ?>> RULE_SORTABLE = Map.of(
        "ruleType", ScheduleRuleEntity::getRuleType,
        "targetType", ScheduleRuleEntity::getTargetType,
        "startDate", ScheduleRuleEntity::getStartDate,
        "endDate", ScheduleRuleEntity::getEndDate,
        "status", ScheduleRuleEntity::getStatus,
        "remark", ScheduleRuleEntity::getRemark,
        "createdAt", ScheduleRuleEntity::getCreatedAt);

    /** 跨机构访问被拒时的提示对象名：「无权访问其他机构的排课规则」 */
    private static final String SUBJECT = "排课规则";

    private final ScheduleRuleMapper ruleMapper;
    private final TeachingClassMapper classMapper;
    private final TeacherMapper teacherMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final ScheduleRuleSupport ruleSupport;

    public ScheduleRuleServiceImpl(ScheduleRuleMapper ruleMapper, TeachingClassMapper classMapper,
                                   TeacherMapper teacherMapper, TimeSlotMapper timeSlotMapper,
                                   ScheduleRuleSupport ruleSupport) {
        this.ruleMapper = ruleMapper;
        this.classMapper = classMapper;
        this.teacherMapper = teacherMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.ruleSupport = ruleSupport;
    }

    @Override
    public PageResult<ScheduleRuleView> pageRules(ScheduleRulePageQuery query) {
        LambdaQueryWrapper<ScheduleRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScheduleRuleEntity::getInstitutionId,
            ScheduleInstitutionGuard.resolveForQuery(query.getInstitutionId(), SUBJECT));
        if (notBlank(query.getRuleType())) {
            wrapper.eq(ScheduleRuleEntity::getRuleType, query.getRuleType().trim());
        }
        if (notBlank(query.getTargetType())) {
            wrapper.eq(ScheduleRuleEntity::getTargetType, query.getTargetType().trim());
        }
        if (query.getTargetId() != null) {
            wrapper.eq(ScheduleRuleEntity::getTargetId, query.getTargetId());
        }
        if (notBlank(query.getStatus())) {
            wrapper.eq(ScheduleRuleEntity::getStatus, query.getStatus().trim());
        }
        if (!PageSortSupport.apply(wrapper, query, RULE_SORTABLE, ScheduleRuleEntity::getId)) {
            wrapper.orderByDesc(ScheduleRuleEntity::getCreatedAt);
        }
        Page<ScheduleRuleEntity> page = ruleMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toView).toList());
    }

    @Override
    public ScheduleRuleView getRule(Long id) {
        return toView(requireRule(id));
    }

    @Override
    @Transactional
    public ScheduleRuleView createRule(ScheduleRuleRequest request) {
        ResolvedRule resolved = resolve(request);
        assertCellsFree(request, resolved, null);
        ScheduleRuleEntity entity = new ScheduleRuleEntity();
        applyDefinition(entity, request, resolved);
        entity.setDeleted(Boolean.FALSE);
        ruleMapper.insert(entity);
        return toView(requireRule(entity.getId()));
    }

    @Override
    @Transactional
    public ScheduleRuleView updateRule(Long id, ScheduleRuleRequest request) {
        ScheduleRuleEntity existing = requireRule(id);
        ResolvedRule resolved = resolve(request);
        assertCellsFree(request, resolved, id);
        applyDefinition(existing, request, resolved);
        ruleMapper.updateById(existing);
        return toView(requireRule(id));
    }

    @Override
    @Transactional
    public void deleteRule(Long id) {
        requireRule(id);
        ruleMapper.deleteById(id);
    }

    private ScheduleRuleEntity requireRule(Long id) {
        ScheduleRuleEntity entity = ruleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "排课规则不存在：" + id);
        }
        ScheduleInstitutionGuard.assertOwn(entity.getInstitutionId(), SUBJECT);
        return entity;
    }

    private void applyDefinition(ScheduleRuleEntity entity, ScheduleRuleRequest request, ResolvedRule resolved) {
        entity.setInstitutionId(request.institutionId());
        entity.setRuleType(resolved.ruleType());
        entity.setTargetType(resolved.targetType());
        entity.setTargetId(request.targetId());
        entity.setSlotsJson(ruleSupport.writeSlots(resolved.slots()));
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(resolved.status());
        entity.setRemark(request.remark());
    }

    /**
     * 校验规则对象与格子的归属：目标必须存在且属于该机构，时段必须与机构/校区及格子星期一致（与排课同一口径）。
     */
    private ResolvedRule resolve(ScheduleRuleRequest request) {
        // 规则只能落在登录机构自己的班级/教师身上：请求声明的机构与登录机构不一致直接拒
        ScheduleInstitutionGuard.assertOwn(request.institutionId(), SUBJECT);
        String ruleType = normalize(request.ruleType());
        if (!List.of(ScheduleRuleSupport.RULE_FORBIDDEN, ScheduleRuleSupport.RULE_REQUIRED).contains(ruleType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "规则类型只能是禁排或必排");
        }
        String targetType = normalize(request.targetType());
        Long targetCampusId;
        if (ScheduleRuleSupport.TARGET_CLASS.equals(targetType)) {
            TeachingClassEntity teachingClass = classMapper.selectById(request.targetId());
            if (teachingClass == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + request.targetId());
            }
            if (!Objects.equals(teachingClass.getInstitutionId(), request.institutionId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "班级与规则的机构不一致");
            }
            targetCampusId = teachingClass.getCampusId();
        } else if (ScheduleRuleSupport.TARGET_TEACHER.equals(targetType)) {
            TeacherEntity teacher = teacherMapper.selectById(request.targetId());
            if (teacher == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "教师不存在：" + request.targetId());
            }
            if (!Objects.equals(teacher.getInstitutionId(), request.institutionId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "教师与规则的机构不一致");
            }
            targetCampusId = teacher.getCampusId();
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "规则对象类型只能是班级或教师");
        }

        List<ScheduleSlotCell> slots = request.slots().stream()
            .filter(slot -> slot != null && slot.weekday() != null && slot.timeSlotId() != null)
            .distinct()
            .toList();
        if (slots.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "至少选择一个节次格子");
        }
        for (ScheduleSlotCell slot : slots) {
            TimeSlotEntity timeSlot = timeSlotMapper.selectById(slot.timeSlotId());
            if (timeSlot == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "时间段不存在：" + slot.timeSlotId());
            }
            if (!Objects.equals(timeSlot.getInstitutionId(), request.institutionId())
                || (timeSlot.getCampusId() != null && !Objects.equals(timeSlot.getCampusId(), targetCampusId))) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "时间段与规则对象不匹配：" + slot.timeSlotId());
            }
            if (!Objects.equals(timeSlot.getWeekday(), slot.weekday())) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "规则星期与时间段星期不一致：" + timeSlot.getSlotName());
            }
        }

        if (request.startDate() != null && request.endDate() != null && request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "规则生效起始日期不能晚于截止日期");
        }
        String status = normalize(request.status());
        if (status.isEmpty()) {
            status = ScheduleRuleSupport.STATUS_ACTIVE;
        }
        if (!List.of(ScheduleRuleSupport.STATUS_ACTIVE, ScheduleRuleSupport.STATUS_INACTIVE).contains(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "规则状态只能是启用或停用");
        }
        return new ResolvedRule(ruleType, targetType, slots, status);
    }

    /**
     * 表上没建唯一键（同一对象允许多条不同区间/格子的规则），重复登记与"同格子既禁排又必排"这类矛盾靠这里拦。
     */
    private void assertCellsFree(ScheduleRuleRequest request, ResolvedRule resolved, Long currentRuleId) {
        Set<String> newCells = new LinkedHashSet<>();
        for (ScheduleSlotCell slot : resolved.slots()) {
            newCells.add(ScheduleRuleSupport.cellKey(slot.weekday(), slot.timeSlotId()));
        }
        LambdaQueryWrapper<ScheduleRuleEntity> wrapper = new LambdaQueryWrapper<ScheduleRuleEntity>()
            .eq(ScheduleRuleEntity::getInstitutionId, request.institutionId())
            .eq(ScheduleRuleEntity::getTargetType, resolved.targetType())
            .eq(ScheduleRuleEntity::getTargetId, request.targetId())
            .eq(ScheduleRuleEntity::getStatus, ScheduleRuleSupport.STATUS_ACTIVE);
        if (currentRuleId != null) {
            wrapper.ne(ScheduleRuleEntity::getId, currentRuleId);
        }
        for (ScheduleRuleEntity existing : ruleMapper.selectList(wrapper)) {
            for (ScheduleSlotCell cell : ruleSupport.readSlots(existing.getSlotsJson())) {
                if (newCells.contains(ScheduleRuleSupport.cellKey(cell.weekday(), cell.timeSlotId()))) {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "该对象已存在覆盖同一格子的启用规则：" + existing.getId()
                            + "（" + ScheduleRuleSupport.weekdayLabel(cell.weekday())
                            + "，类型 " + existing.getRuleType() + "）");
                }
            }
        }
    }

    private ScheduleRuleView toView(ScheduleRuleEntity entity) {
        return new ScheduleRuleView(
            entity.getId(),
            entity.getInstitutionId(),
            entity.getRuleType(),
            entity.getTargetType(),
            entity.getTargetId(),
            ruleSupport.readSlots(entity.getSlotsJson()),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.getStatus(),
            entity.getRemark(),
            entity.getCreatedAt()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record ResolvedRule(String ruleType, String targetType, List<ScheduleSlotCell> slots, String status) {
    }
}
