package com.jiaopei.platform.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.organization.dto.ClassroomCreateRequest;
import com.jiaopei.platform.organization.dto.ClassroomUpdateRequest;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.ClassroomMapper;
import com.jiaopei.platform.organization.service.ClassroomService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomMapper classroomMapper;
    private final CampusMapper campusMapper;

    public ClassroomServiceImpl(ClassroomMapper classroomMapper, CampusMapper campusMapper) {
        this.classroomMapper = classroomMapper;
        this.campusMapper = campusMapper;
    }

    @Override
    public List<ClassroomEntity> listClassrooms(Long campusId) {
        LambdaQueryWrapper<ClassroomEntity> wrapper = new LambdaQueryWrapper<ClassroomEntity>()
            .orderByAsc(ClassroomEntity::getCampusId)
            .orderByAsc(ClassroomEntity::getClassroomCode);
        if (campusId != null) {
            wrapper.eq(ClassroomEntity::getCampusId, campusId);
        }
        return classroomMapper.selectList(wrapper);
    }

    @Override
    public ClassroomEntity createClassroom(ClassroomCreateRequest request) {
        CampusEntity campus = campusMapper.selectById(request.campusId());
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Campus does not exist: " + request.campusId());
        }
        String classroomCode = normalize(request.classroomCode());
        String classroomName = normalize(request.classroomName());
        assertUniqueClassroomCode(request.campusId(), classroomCode, null);
        assertUniqueClassroomName(request.campusId(), classroomName, null);
        if (request.areaSqm() != null && request.areaSqm().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "areaSqm must be greater than or equal to 0");
        }

        ClassroomEntity entity = new ClassroomEntity();
        entity.setCampusId(request.campusId());
        entity.setClassroomCode(classroomCode);
        entity.setClassroomName(classroomName);
        entity.setRoomType(request.roomType() == null || request.roomType().isBlank() ? "normal" : request.roomType());
        entity.setFloorNo(request.floorNo());
        entity.setSeatCapacity(request.seatCapacity());
        entity.setAreaSqm(request.areaSqm());
        entity.setEquipmentTags(request.equipmentTags());
        entity.setStatus(request.status() == null || request.status().isBlank() ? "idle" : request.status());
        entity.setAllowScheduling(request.allowScheduling() == null ? Boolean.TRUE : request.allowScheduling());
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            classroomMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Classroom classroomCode or classroomName already exists in campus: " + request.campusId());
        }
        return entity;
    }

    @Override
    public ClassroomEntity updateClassroom(Long id, ClassroomUpdateRequest request) {
        ClassroomEntity existing = requireClassroom(id);
        Long campusId = request.campusId() == null ? existing.getCampusId() : request.campusId();
        if (request.campusId() != null && !Objects.equals(existing.getCampusId(), request.campusId())) {
            CampusEntity campus = campusMapper.selectById(request.campusId());
            if (campus == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "Campus does not exist: " + request.campusId());
            }
            existing.setCampusId(request.campusId());
        }
        if (request.classroomCode() != null && !request.classroomCode().isBlank()) {
            String classroomCode = normalize(request.classroomCode());
            if (!Objects.equals(existing.getClassroomCode(), classroomCode)) {
                assertUniqueClassroomCode(campusId, classroomCode, id);
                existing.setClassroomCode(classroomCode);
            }
        }
        if (request.classroomName() != null && !request.classroomName().isBlank()) {
            String classroomName = normalize(request.classroomName());
            if (!Objects.equals(existing.getClassroomName(), classroomName)) {
                assertUniqueClassroomName(campusId, classroomName, id);
                existing.setClassroomName(classroomName);
            }
        }
        if (request.roomType() != null && !request.roomType().isBlank()) {
            existing.setRoomType(request.roomType().trim());
        }
        if (request.floorNo() != null) {
            existing.setFloorNo(normalizeToNull(request.floorNo()));
        }
        if (request.seatCapacity() != null) {
            existing.setSeatCapacity(request.seatCapacity());
        }
        if (request.areaSqm() != null) {
            if (request.areaSqm().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "areaSqm must be greater than or equal to 0");
            }
            existing.setAreaSqm(request.areaSqm());
        }
        if (request.equipmentTags() != null) {
            existing.setEquipmentTags(normalizeToNull(request.equipmentTags()));
        }
        if (request.status() != null && !request.status().isBlank()) {
            existing.setStatus(request.status().trim());
        }
        if (request.allowScheduling() != null) {
            existing.setAllowScheduling(request.allowScheduling());
        }
        if (request.remark() != null) {
            existing.setRemark(normalizeToNull(request.remark()));
        }
        try {
            classroomMapper.updateById(existing);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Classroom classroomCode or classroomName already exists in campus: " + existing.getCampusId());
        }
        return existing;
    }

    @Override
    public void deleteClassroom(Long id) {
        requireClassroom(id);
        classroomMapper.deleteById(id);
    }

    private ClassroomEntity requireClassroom(Long id) {
        ClassroomEntity entity = classroomMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Classroom does not exist: " + id);
        }
        return entity;
    }

    private void assertUniqueClassroomCode(Long campusId, String classroomCode, Long excludeId) {
        LambdaQueryWrapper<ClassroomEntity> wrapper = new LambdaQueryWrapper<ClassroomEntity>()
            .eq(ClassroomEntity::getCampusId, campusId)
            .eq(ClassroomEntity::getClassroomCode, classroomCode);
        if (excludeId != null) {
            wrapper.ne(ClassroomEntity::getId, excludeId);
        }
        Long count = classroomMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Classroom classroomCode already exists in campus: " + campusId);
        }
    }

    private void assertUniqueClassroomName(Long campusId, String classroomName, Long excludeId) {
        LambdaQueryWrapper<ClassroomEntity> wrapper = new LambdaQueryWrapper<ClassroomEntity>()
            .eq(ClassroomEntity::getCampusId, campusId)
            .eq(ClassroomEntity::getClassroomName, classroomName);
        if (excludeId != null) {
            wrapper.ne(ClassroomEntity::getId, excludeId);
        }
        Long count = classroomMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Classroom classroomName already exists in campus: " + campusId);
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
