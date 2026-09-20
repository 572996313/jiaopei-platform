package com.cornerstone.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import com.cornerstone.module.organization.domain.entity.TimeSlotTemplateEntity;
import com.cornerstone.module.organization.dto.TimeSlotApplyRequest;
import com.cornerstone.module.organization.dto.TimeSlotApplyResponse;
import com.cornerstone.module.organization.dto.TimeSlotApplySlot;
import com.cornerstone.module.organization.dto.TimeSlotTemplateCreateRequest;
import com.cornerstone.module.organization.dto.TimeSlotTemplateUpdateRequest;
import com.cornerstone.module.organization.dto.TimeSlotTemplateView;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.TimeSlotMapper;
import com.cornerstone.module.organization.mapper.TimeSlotTemplateMapper;
import com.cornerstone.module.organization.service.TimeSlotTemplateService;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeSlotTemplateServiceImpl implements TimeSlotTemplateService {

    private static final String DEFAULT_SLOT_TYPE = "teaching";

    private final TimeSlotTemplateMapper templateMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final CampusMapper campusMapper;
    private final ObjectMapper objectMapper;

    public TimeSlotTemplateServiceImpl(TimeSlotTemplateMapper templateMapper, TimeSlotMapper timeSlotMapper,
                                       CampusMapper campusMapper, ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.campusMapper = campusMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TimeSlotTemplateView> listTemplates(Long operatorInstitutionId, Long institutionId, Long campusId) {
        Long effectiveInstitutionId = resolveInstitutionId(operatorInstitutionId, institutionId);
        LambdaQueryWrapper<TimeSlotTemplateEntity> wrapper = new LambdaQueryWrapper<TimeSlotTemplateEntity>()
            .eq(TimeSlotTemplateEntity::getInstitutionId, effectiveInstitutionId)
            .orderByAsc(TimeSlotTemplateEntity::getCreatedAt);
        if (campusId != null) {
            wrapper.eq(TimeSlotTemplateEntity::getCampusId, campusId);
        }
        return templateMapper.selectList(wrapper).stream().map(this::toView).toList();
    }

    @Override
    public TimeSlotTemplateView createTemplate(Long operatorInstitutionId, TimeSlotTemplateCreateRequest request) {
        assertSameInstitution(operatorInstitutionId, request.institutionId());
        validateCampusScope(request.institutionId(), request.campusId());
        assertConfigIsObject(request.config());
        String templateName = requireText(request.templateName(), "模板名称不能为空");
        assertUniqueTemplateName(request.institutionId(), request.campusId(), templateName, null);

        TimeSlotTemplateEntity entity = new TimeSlotTemplateEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setTemplateName(templateName);
        entity.setConfigJson(request.config().toString());
        entity.setStatus("active");
        entity.setRemark(trimToNull(request.remark()));
        entity.setDeleted(Boolean.FALSE);
        try {
            templateMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同机构/校区下模板名称已存在：" + templateName);
        }
        return toView(entity);
    }

    @Override
    public TimeSlotTemplateView updateTemplate(Long operatorInstitutionId, Long id, TimeSlotTemplateUpdateRequest request) {
        TimeSlotTemplateEntity existing = requireTemplate(operatorInstitutionId, id);
        if (request.templateName() != null && !request.templateName().isBlank()) {
            String templateName = request.templateName().trim();
            if (!Objects.equals(existing.getTemplateName(), templateName)) {
                assertUniqueTemplateName(existing.getInstitutionId(), existing.getCampusId(), templateName, id);
                existing.setTemplateName(templateName);
            }
        }
        if (request.config() != null) {
            assertConfigIsObject(request.config());
            existing.setConfigJson(request.config().toString());
        }
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(request.status().trim());
        }
        if (request.remark() != null) {
            existing.setRemark(trimToNull(request.remark()));
        }
        try {
            templateMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同机构/校区下模板名称已存在");
        }
        return toView(existing);
    }

    @Override
    public void deleteTemplate(Long operatorInstitutionId, Long id) {
        requireTemplate(operatorInstitutionId, id);
        Long slotCount = timeSlotMapper.selectCount(new LambdaQueryWrapper<TimeSlotEntity>()
            .eq(TimeSlotEntity::getTemplateId, id));
        if (slotCount != null && slotCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "该模板下还有 " + slotCount + " 条时间段,请先在网格编辑器中移除后再删除模板");
        }
        templateMapper.deletePhysically(id);
    }

    @Override
    @Transactional
    public TimeSlotApplyResponse applyTemplate(Long operatorInstitutionId, Long id, TimeSlotApplyRequest request) {
        TimeSlotTemplateEntity template = requireTemplate(operatorInstitutionId, id);
        List<NormalizedSlot> slots = normalizeAndValidate(request.slots());

        Long institutionId = template.getInstitutionId();
        Long campusId = template.getCampusId();

        // 本模板存量行,按 星期|编码 匹配以复用主键(schedule_lesson 外键指向时间段行,禁止删旧插新)
        List<TimeSlotEntity> ownedRows = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlotEntity>()
            .eq(TimeSlotEntity::getTemplateId, template.getId()));
        Map<String, TimeSlotEntity> ownedByKey = new HashMap<>();
        for (TimeSlotEntity row : ownedRows) {
            ownedByKey.put(slotKey(row.getWeekday(), row.getSlotCode()), row);
        }

        // 非本模板的行(手工录入 template_id 为空 / 其他模板)占用同范围编码与名称,冲突直接拒绝
        List<TimeSlotEntity> foreignRows = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlotEntity>()
            .eq(TimeSlotEntity::getInstitutionId, institutionId)
            .and(campusId == null,
                w -> w.isNull(TimeSlotEntity::getCampusId))
            .eq(campusId != null, TimeSlotEntity::getCampusId, campusId)
            .and(w -> w.isNull(TimeSlotEntity::getTemplateId).or().ne(TimeSlotEntity::getTemplateId, template.getId())));
        Set<String> foreignCodes = new HashSet<>();
        Set<String> foreignNames = new HashSet<>();
        for (TimeSlotEntity row : foreignRows) {
            foreignCodes.add(slotKey(row.getWeekday(), row.getSlotCode()));
            foreignNames.add(slotKey(row.getWeekday(), row.getSlotName()));
        }
        for (NormalizedSlot slot : slots) {
            if (foreignCodes.contains(slotKey(slot.weekday(), slot.slotCode()))
                || foreignNames.contains(slotKey(slot.weekday(), slot.slotName()))) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "节次「" + slot.slotName() + "」与手工录入或其他模板的时间段编码/名称冲突,请先调整");
            }
        }

        int created = 0;
        int updated = 0;
        Set<Long> matchedIds = new HashSet<>();
        for (NormalizedSlot slot : slots) {
            TimeSlotEntity existing = ownedByKey.get(slotKey(slot.weekday(), slot.slotCode()));
            if (existing != null) {
                matchedIds.add(existing.getId());
                existing.setSlotName(slot.slotName());
                existing.setStartTime(slot.startTime());
                existing.setEndTime(slot.endTime());
                existing.setDurationMinutes(slot.durationMinutes());
                existing.setSlotType(slot.slotType());
                existing.setSortNo(slot.sortNo() == null ? existing.getSortNo() : slot.sortNo());
                existing.setStatus("active");
                timeSlotMapper.updateById(existing);
                updated++;
            } else {
                TimeSlotEntity entity = new TimeSlotEntity();
                entity.setInstitutionId(institutionId);
                entity.setCampusId(campusId);
                entity.setSlotCode(slot.slotCode());
                entity.setSlotName(slot.slotName());
                entity.setWeekday(slot.weekday());
                entity.setStartTime(slot.startTime());
                entity.setEndTime(slot.endTime());
                entity.setDurationMinutes(slot.durationMinutes());
                entity.setSlotType(slot.slotType());
                entity.setStatus("active");
                entity.setTemplateId(template.getId());
                entity.setSortNo(slot.sortNo());
                entity.setDeleted(Boolean.FALSE);
                timeSlotMapper.insert(entity);
                created++;
            }
        }

        // 网格中已移除的旧行:未被排课引用物理删除,被引用改为停用(保留行避免课次外键悬空)
        int removed = 0;
        int deactivated = 0;
        for (TimeSlotEntity row : ownedRows) {
            if (matchedIds.contains(row.getId())) {
                continue;
            }
            if (timeSlotMapper.countLessonRefs(row.getId()) > 0) {
                row.setStatus("inactive");
                timeSlotMapper.updateById(row);
                deactivated++;
            } else {
                timeSlotMapper.deletePhysically(row.getId());
                removed++;
            }
        }

        if (request.config() != null) {
            assertConfigIsObject(request.config());
            template.setConfigJson(request.config().toString());
            templateMapper.updateById(template);
        }

        List<TimeSlotEntity> result = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlotEntity>()
            .eq(TimeSlotEntity::getTemplateId, template.getId())
            .orderByAsc(TimeSlotEntity::getWeekday)
            .orderByAsc(TimeSlotEntity::getSortNo)
            .orderByAsc(TimeSlotEntity::getStartTime));
        return new TimeSlotApplyResponse(created, updated, removed, deactivated, result);
    }

    /** 校验并规整节次:编码/名称去空格、时长由服务端按起止时间重算、同星期去重且不允许时间重叠 */
    private List<NormalizedSlot> normalizeAndValidate(List<TimeSlotApplySlot> rawSlots) {
        List<NormalizedSlot> slots = new ArrayList<>(rawSlots.size());
        Map<Integer, List<NormalizedSlot>> byWeekday = new HashMap<>();
        for (TimeSlotApplySlot slot : rawSlots) {
            if (slot.weekday() == null || slot.weekday() < 1 || slot.weekday() > 7) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "星期必须在1到7之间");
            }
            String slotCode = requireText(slot.slotCode(), "时段编码不能为空");
            String slotName = requireText(slot.slotName(), "时段名称不能为空");
            LocalTime startTime = slot.startTime();
            LocalTime endTime = slot.endTime();
            if (startTime == null || endTime == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "时段「" + slotName + "」缺少开始或结束时间");
            }
            if (!startTime.isBefore(endTime)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "时段「" + slotName + "」开始时间必须早于结束时间");
            }
            NormalizedSlot normalized = new NormalizedSlot(
                slot.weekday(),
                slotCode,
                slotName,
                startTime,
                endTime,
                (int) ChronoUnit.MINUTES.between(startTime, endTime),
                slot.slotType() == null || slot.slotType().isBlank() ? DEFAULT_SLOT_TYPE : slot.slotType().trim(),
                slot.sortNo());
            slots.add(normalized);
            byWeekday.computeIfAbsent(normalized.weekday(), key -> new ArrayList<>()).add(normalized);
        }
        for (Map.Entry<Integer, List<NormalizedSlot>> entry : byWeekday.entrySet()) {
            List<NormalizedSlot> daySlots = entry.getValue();
            Set<String> codes = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (NormalizedSlot slot : daySlots) {
                if (!codes.add(slot.slotCode())) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "星期" + entry.getKey() + "存在重复时段编码：" + slot.slotCode());
                }
                if (!names.add(slot.slotName())) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "星期" + entry.getKey() + "存在重复时段名称：" + slot.slotName());
                }
            }
            daySlots.sort(Comparator.comparing(NormalizedSlot::startTime));
            for (int i = 1; i < daySlots.size(); i++) {
                if (daySlots.get(i).startTime().isBefore(daySlots.get(i - 1).endTime())) {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "星期" + entry.getKey() + "时段「" + daySlots.get(i).slotName() + "」与「" + daySlots.get(i - 1).slotName() + "」时间重叠");
                }
            }
        }
        return slots;
    }

    /** 规整后的节次(服务端可信值) */
    private record NormalizedSlot(Integer weekday, String slotCode, String slotName,
                                  LocalTime startTime, LocalTime endTime, int durationMinutes,
                                  String slotType, Integer sortNo) {
    }

    private TimeSlotTemplateEntity requireTemplate(Long operatorInstitutionId, Long id) {
        TimeSlotTemplateEntity entity = templateMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "时段模板不存在：" + id);
        }
        assertSameInstitution(operatorInstitutionId, entity.getInstitutionId());
        return entity;
    }

    private void validateCampusScope(Long institutionId, Long campusId) {
        if (campusId == null) {
            return;
        }
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + campusId);
        }
        if (!Objects.equals(campus.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "校区不属于该机构：" + institutionId);
        }
    }

    private void assertUniqueTemplateName(Long institutionId, Long campusId, String templateName, Long excludeId) {
        LambdaQueryWrapper<TimeSlotTemplateEntity> wrapper = new LambdaQueryWrapper<TimeSlotTemplateEntity>()
            .eq(TimeSlotTemplateEntity::getInstitutionId, institutionId)
            .eq(TimeSlotTemplateEntity::getTemplateName, templateName);
        if (campusId == null) {
            wrapper.isNull(TimeSlotTemplateEntity::getCampusId);
        } else {
            wrapper.eq(TimeSlotTemplateEntity::getCampusId, campusId);
        }
        if (excludeId != null) {
            wrapper.ne(TimeSlotTemplateEntity::getId, excludeId);
        }
        Long count = templateMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "同机构/校区下模板名称已存在：" + templateName);
        }
    }

    private void assertConfigIsObject(JsonNode config) {
        if (config == null || !config.isObject()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "节次参数必须是JSON对象");
        }
    }

    private TimeSlotTemplateView toView(TimeSlotTemplateEntity entity) {
        JsonNode config;
        try {
            config = objectMapper.readTree(entity.getConfigJson());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "模板参数快照损坏：" + entity.getId());
        }
        return new TimeSlotTemplateView(
            entity.getId(),
            entity.getInstitutionId(),
            entity.getCampusId(),
            entity.getTemplateName(),
            config,
            entity.getStatus(),
            entity.getRemark(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /** 租户隔离:机构级参数只作筛选,与登录用户所属机构不符直接拒绝;缺省回落为登录用户机构 */
    private Long resolveInstitutionId(Long operatorInstitutionId, Long requestedInstitutionId) {
        if (requestedInstitutionId == null) {
            return operatorInstitutionId;
        }
        assertSameInstitution(operatorInstitutionId, requestedInstitutionId);
        return requestedInstitutionId;
    }

    private void assertSameInstitution(Long operatorInstitutionId, Long requestedInstitutionId) {
        if (operatorInstitutionId == null || !Objects.equals(operatorInstitutionId, requestedInstitutionId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他机构的时段模板");
        }
    }

    private String slotKey(Integer weekday, String value) {
        return weekday + "|" + value;
    }

    private String requireText(String value, String message) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
