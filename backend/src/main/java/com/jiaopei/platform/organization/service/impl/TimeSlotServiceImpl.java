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
    public List<TimeSlotEntity> listTimeSlots(Long institutionId) {
        LambdaQueryWrapper<TimeSlotEntity> wrapper = new LambdaQueryWrapper<TimeSlotEntity>()
            .orderByAsc(TimeSlotEntity::getInstitutionId)
            .orderByAsc(TimeSlotEntity::getWeekday)
            .orderByAsc(TimeSlotEntity::getSortNo)
            .orderByAsc(TimeSlotEntity::getStartTime);
        if (institutionId != null) {
            wrapper.eq(TimeSlotEntity::getInstitutionId, institutionId);
        }
        return timeSlotMapper.selectList(wrapper);
    }

    @Override
    public TimeSlotEntity createTimeSlot(TimeSlotCreateRequest request) {
        InstitutionEntity institution = institutionMapper.selectById(request.institutionId());
        if (institution == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Institution does not exist: " + request.institutionId());
        }
        if (request.campusId() != null) {
            CampusEntity campus = campusMapper.selectById(request.campusId());
            if (campus == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "Campus does not exist: " + request.campusId());
            }
            if (!Objects.equals(campus.getInstitutionId(), request.institutionId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Campus does not belong to institution: " + request.institutionId());
            }
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "startTime must be earlier than endTime");
        }
        long minutes = ChronoUnit.MINUTES.between(request.startTime(), request.endTime());
        if (minutes != request.durationMinutes()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "durationMinutes must match startTime and endTime");
        }
        String slotCode = normalize(request.slotCode());
        String slotName = normalize(request.slotName());
        assertUniqueSlotCode(request.institutionId(), slotCode, null);
        assertUniqueSlotName(request.institutionId(), request.campusId(), slotName, null);

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
            throw new BusinessException(ResultCode.BAD_REQUEST, "Time slot slotCode or slotName already exists in institution: " + request.institutionId());
        }
        return entity;
    }

    @Override
    public TimeSlotEntity updateTimeSlot(Long id, TimeSlotUpdateRequest request) {
        TimeSlotEntity existing = requireTimeSlot(id);
        Long campusId = request.campusId() == null ? existing.getCampusId() : request.campusId();
        Long validatingCampusId = campusId;
        if (request.campusId() != null && !Objects.equals(existing.getCampusId(), request.campusId())) {
            CampusEntity campus = campusMapper.selectById(request.campusId());
            if (campus == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "Campus does not exist: " + request.campusId());
            }
            if (!Objects.equals(campus.getInstitutionId(), existing.getInstitutionId())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Campus does not belong to institution: " + existing.getInstitutionId());
            }
            existing.setCampusId(request.campusId());
        }
        LocalTime startTime = request.startTime() == null ? existing.getStartTime() : request.startTime();
        LocalTime endTime = request.endTime() == null ? existing.getEndTime() : request.endTime();
        Integer durationMinutes = request.durationMinutes() == null ? existing.getDurationMinutes() : request.durationMinutes();
        if (!startTime.isBefore(endTime)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "startTime must be earlier than endTime");
        }
        long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
        if (minutes != durationMinutes) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "durationMinutes must match startTime and endTime");
        }
        if (request.slotCode() != null && !request.slotCode().isBlank()) {
            String slotCode = normalize(request.slotCode());
            if (!Objects.equals(existing.getSlotCode(), slotCode)) {
                assertUniqueSlotCode(existing.getInstitutionId(), slotCode, id);
                existing.setSlotCode(slotCode);
            }
        }
        if (request.slotName() != null && !request.slotName().isBlank()) {
            String slotName = normalize(request.slotName());
            if (!Objects.equals(existing.getSlotName(), slotName)) {
                assertUniqueSlotName(existing.getInstitutionId(), validatingCampusId, slotName, id);
                existing.setSlotName(slotName);
            }
        }
        if (request.weekday() != null) {
            if (request.weekday() < 1 || request.weekday() > 7) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "weekday must be between 1 and 7");
            }
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "Time slot slotCode or slotName already exists in institution: " + existing.getInstitutionId());
        }
        return existing;
    }

    @Override
    public void deleteTimeSlot(Long id) {
        requireTimeSlot(id);
        timeSlotMapper.deleteById(id);
    }

    private TimeSlotEntity requireTimeSlot(Long id) {
        TimeSlotEntity entity = timeSlotMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Time slot does not exist: " + id);
        }
        return entity;
    }

    private void assertUniqueSlotCode(Long institutionId, String slotCode, Long excludeId) {
        LambdaQueryWrapper<TimeSlotEntity> wrapper = new LambdaQueryWrapper<TimeSlotEntity>()
            .eq(TimeSlotEntity::getInstitutionId, institutionId)
            .eq(TimeSlotEntity::getSlotCode, slotCode);
        if (excludeId != null) {
            wrapper.ne(TimeSlotEntity::getId, excludeId);
        }
        Long count = timeSlotMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Time slot slotCode already exists in institution: " + institutionId);
        }
    }

    private void assertUniqueSlotName(Long institutionId, Long campusId, String slotName, Long excludeId) {
        LambdaQueryWrapper<TimeSlotEntity> wrapper = new LambdaQueryWrapper<TimeSlotEntity>()
            .eq(TimeSlotEntity::getInstitutionId, institutionId)
            .eq(TimeSlotEntity::getSlotName, slotName);
        if (campusId == null) {
            wrapper.isNull(TimeSlotEntity::getCampusId);
        } else {
            wrapper.eq(TimeSlotEntity::getCampusId, campusId);
        }
        if (excludeId != null) {
            wrapper.ne(TimeSlotEntity::getId, excludeId);
        }
        Long count = timeSlotMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Time slot slotName already exists in institution/campus scope: " + institutionId);
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
