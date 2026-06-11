package com.jiaopei.platform.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.organization.domain.entity.CampusEntity;
import com.jiaopei.platform.organization.domain.entity.ClassroomEntity;
import com.jiaopei.platform.organization.dto.ClassroomCreateRequest;
import com.jiaopei.platform.organization.mapper.CampusMapper;
import com.jiaopei.platform.organization.mapper.ClassroomMapper;
import com.jiaopei.platform.organization.service.ClassroomService;
import java.math.BigDecimal;
import java.util.List;
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
        assertUniqueClassroomCode(request.campusId(), classroomCode);
        assertUniqueClassroomName(request.campusId(), classroomName);
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

    private void assertUniqueClassroomCode(Long campusId, String classroomCode) {
        Long count = classroomMapper.selectCount(new LambdaQueryWrapper<ClassroomEntity>()
            .eq(ClassroomEntity::getCampusId, campusId)
            .eq(ClassroomEntity::getClassroomCode, classroomCode));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Classroom classroomCode already exists in campus: " + campusId);
        }
    }

    private void assertUniqueClassroomName(Long campusId, String classroomName) {
        Long count = classroomMapper.selectCount(new LambdaQueryWrapper<ClassroomEntity>()
            .eq(ClassroomEntity::getCampusId, campusId)
            .eq(ClassroomEntity::getClassroomName, classroomName));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Classroom classroomName already exists in campus: " + campusId);
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
