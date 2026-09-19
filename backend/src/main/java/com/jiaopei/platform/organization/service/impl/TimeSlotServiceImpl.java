package com.jiaopei.platform.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.domain.entity.InstitutionEntity;
import com.jiaopei.platform.organization.domain.entity.TimeSlotEntity;
import com.jiaopei.platform.organization.dto.TimeSlotCreateRequest;
import com.jiaopei.platform.organization.dto.TimeSlotUpdateRequest;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.InstitutionMapper;
import com.jiaopei.platform.organization.mapper.TimeSlotMapper;
import com.jiaopei.platform.organization.service.TimeSlotService;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TimeSlotServiceImpl implements TimeSlotService {

    private final TimeSlotMapper timeSlotMapper;
    private final InstitutionMapper institutionMapper;
    private final CampusMapper campusMapper;

    public TimeSlotServiceImpl(TimeSlotMapper timeSlotMapper, InstitutionMapper institutionMapper,
                               CampusMapper campusMapper) {
        this.timeSlotMapper = timeSlotMapper;
        this.institutionMapper = institutionMapper;
        this.campusMapper = campusMapper;
    }

    @Override
    public List<TimeSlotEntity> listTimeSlots(Long operatorInstitutionId, Long institutionId) {
        Long effectiveInstitutionId = resolveInstitutionId(operatorInstitutionId, institutionId);
        LambdaQueryWrapper<TimeSlotEntity> wrapper = new LambdaQueryWrapper<TimeSlotEntity>()
            .eq(TimeSlotEntity::getInstitutionId, effectiveInstitutionId)
            .orderByAsc(TimeSlotEntity::getWeekday)
            .orderByAsc(TimeSlotEntity::getSortNo)
            .orderByAsc(TimeSlotEntity::getStartTime);
        return timeSlotMapper.selectList(wrapper);
    }

    @Override
    public TimeSlotEntity createTimeSlot(Long operatorInstitutionId, TimeSlotCreateRequest request) {
        assertSameInstitution(operatorInstitutionId, request.institutionId());
        InstitutionEntity institution = institutionMapper.selectById(request.institutionId());
        if (institution == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "机构不存在：" + request.institutionId());
        }
        if (request.campusId() != null) {
            CampusEntity campus = campusMapper.selectById(request.campusId());
            if (campus == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + request.campusId());
            }
            if (!Objects.equals(campus.getInstitutionId(), request.institutionId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "校区不属于该机构：" + request.institutionId());
            }
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始时间必须早于结束时间");
        }
        long minutes = ChronoUnit.MINUTES.between(request.startTime(), request.endTime());
        if (minutes != request.durationMinutes()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "时长(分钟)必须与开始时间和结束时间一致");
        }
        String slotCode = normalize(request.slotCode());
        String slotName = normalize(request.slotName());
        assertUniqueSlotCode(request.institutionId(), request.campusId(), request.weekday(), slotCode, null);
        assertUniqueSlotName(request.institutionId(), request.campusId(), request.weekday(), slotName, null);

        TimeSlotEntity entity = new TimeSlotEntity();
        entity.setInstitutionId(request.institutionId());
        entity.setCampusId(request.campusId());
        entity.setSlotCode(slotCode);
        entity.setSlotName(slotName);
        entity.setWeekday(request.weekday());
        entity.setStartTime(request.startTime());
        entity.setEndTime(request.endTime());
        entity.setDurationMinutes(request.durationMinutes());
        entity.setSlotType(request.slotType() == null || request.slotType().isBlank() ? "teaching" : request.slotType());
        entity.setApplicableScene(request.applicableScene());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "active" : request.status());
        entity.setSortNo(request.sortNo());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            timeSlotMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构/校区/星期下时间段编码或名称已存在：" + request.institutionId());
        }
        return entity;
    }

    @Override
    public TimeSlotEntity updateTimeSlot(Long operatorInstitutionId, Long id, TimeSlotUpdateRequest request) {
        TimeSlotEntity existing = requireTimeSlot(operatorInstitutionId, id);
        Long campusId = request.campusId() == null ? existing.getCampusId() : request.campusId();
        Long validatingCampusId = campusId;
        if (request.campusId() != null && !Objects.equals(existing.getCampusId(), request.campusId())) {
            CampusEntity campus = campusMapper.selectById(request.campusId());
            if (campus == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + request.campusId());
            }
            if (!Objects.equals(campus.getInstitutionId(), existing.getInstitutionId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "校区不属于该机构：" + existing.getInstitutionId());
            }
            existing.setCampusId(request.campusId());
        }
        LocalTime startTime = request.startTime() == null ? existing.getStartTime() : request.startTime();
        LocalTime endTime = request.endTime() == null ? existing.getEndTime() : request.endTime();
        Integer durationMinutes = request.durationMinutes() == null ? existing.getDurationMinutes() : request.durationMinutes();
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "开始时间必须早于结束时间");
        }
        long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
        if (minutes != durationMinutes) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "时长(分钟)必须与开始时间和结束时间一致");
        }
        Integer weekday = request.weekday() == null ? existing.getWeekday() : request.weekday();
        if (request.weekday() != null && (request.weekday() < 1 || request.weekday() > 7)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "星期必须在1到7之间");
        }
        if (request.slotCode() != null && !request.slotCode().isBlank()) {
            String slotCode = normalize(request.slotCode());
            if (!Objects.equals(existing.getSlotCode(), slotCode)) {
                assertUniqueSlotCode(existing.getInstitutionId(), validatingCampusId, weekday, slotCode, id);
                existing.setSlotCode(slotCode);
            }
        }
        if (request.slotName() != null && !request.slotName().isBlank()) {
            String slotName = normalize(request.slotName());
            if (!Objects.equals(existing.getSlotName(), slotName)) {
                assertUniqueSlotName(existing.getInstitutionId(), validatingCampusId, weekday, slotName, id);
                existing.setSlotName(slotName);
            }
        }
        if (request.weekday() != null) {
            existing.setWeekday(request.weekday());
        }
        if (request.startTime() != null) {
            existing.setStartTime(request.startTime());
        }
        if (request.endTime() != null) {
            existing.setEndTime(request.endTime());
        }
        if (request.durationMinutes() != null) {
            existing.setDurationMinutes(request.durationMinutes());
        }
        if (request.slotType() != null && !request.slotType().isBlank()) {
            existing.setSlotType(request.slotType().trim());
        }
        if (request.applicableScene() != null) {
            existing.setApplicableScene(normalizeToNull(request.applicableScene()));
        }
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(request.status().trim());
        }
        if (request.sortNo() != null) {
            existing.setSortNo(request.sortNo());
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        try {
            timeSlotMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构/校区/星期下时间段编码或名称已存在：" + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteTimeSlot(Long operatorInstitutionId, Long id) {
        requireTimeSlot(operatorInstitutionId, id);
        // 唯一键含 deleted,软删残留会永久占用编码/名称;被引用行无法物理删除(外键),引导改用停用
        if (timeSlotMapper.countLessonRefs(id) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该时间段已被排课引用,无法删除,请改为停用");
        }
        timeSlotMapper.deletePhysically(id);
    }

    private TimeSlotEntity requireTimeSlot(Long operatorInstitutionId, Long id) {
        TimeSlotEntity entity = timeSlotMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "时间段不存在：" + id);
        }
        assertSameInstitution(operatorInstitutionId, entity.getInstitutionId());
        return entity;
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
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他机构的时间段数据");
        }
    }

    private void assertUniqueSlotCode(Long institutionId, Long campusId, Integer weekday, String slotCode, Long excludeId) {
        LambdaQueryWrapper<TimeSlotEntity> wrapper = new LambdaQueryWrapper<TimeSlotEntity>()
            .eq(TimeSlotEntity::getInstitutionId, institutionId)
            .eq(TimeSlotEntity::getSlotCode, slotCode);
        applyScope(wrapper, campusId, weekday, excludeId);
        Long count = timeSlotMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构/校区/星期下时间段编码已存在：" + slotCode);
        }
    }

    private void assertUniqueSlotName(Long institutionId, Long campusId, Integer weekday, String slotName, Long excludeId) {
        LambdaQueryWrapper<TimeSlotEntity> wrapper = new LambdaQueryWrapper<TimeSlotEntity>()
            .eq(TimeSlotEntity::getInstitutionId, institutionId)
            .eq(TimeSlotEntity::getSlotName, slotName);
        applyScope(wrapper, campusId, weekday, excludeId);
        Long count = timeSlotMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该机构/校区/星期下时间段名称已存在：" + slotName);
        }
    }

    private void applyScope(LambdaQueryWrapper<TimeSlotEntity> wrapper, Long campusId, Integer weekday, Long excludeId) {
        if (campusId == null) {
            wrapper.isNull(TimeSlotEntity::getCampusId);
        } else {
            wrapper.eq(TimeSlotEntity::getCampusId, campusId);
        }
        if (weekday != null) {
            wrapper.eq(TimeSlotEntity::getWeekday, weekday);
        }
        if (excludeId != null) {
            wrapper.ne(TimeSlotEntity::getId, excludeId);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }
}
