package com.cornerstone.module.organization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.organization.domain.entity.CampusEntity;
import com.cornerstone.module.organization.domain.entity.ClassroomEntity;
import com.cornerstone.module.organization.dto.ClassroomCreateRequest;
import com.cornerstone.module.organization.dto.ClassroomUpdateRequest;
import com.cornerstone.module.organization.mapper.CampusMapper;
import com.cornerstone.module.organization.mapper.ClassroomMapper;
import com.cornerstone.core.system.service.CodeRuleService;
import com.cornerstone.module.organization.service.ClassroomService;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import com.cornerstone.module.organization.service.support.DictValueValidator;
import com.cornerstone.module.organization.service.support.InstitutionScopeGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomMapper classroomMapper;
    private final CampusMapper campusMapper;
    private final DictValueValidator dictValueValidator;
    private final CodeRuleService codeRuleService;

    public ClassroomServiceImpl(ClassroomMapper classroomMapper, CampusMapper campusMapper,
                                DictValueValidator dictValueValidator, CodeRuleService codeRuleService) {
        this.classroomMapper = classroomMapper;
        this.campusMapper = campusMapper;
        this.dictValueValidator = dictValueValidator;
        this.codeRuleService = codeRuleService;
    }

    @Override
    public List<ClassroomEntity> listClassrooms(Long operatorInstitutionId, Long campusId) {
        Long institutionId = InstitutionScopeGuard.resolveForQuery(operatorInstitutionId, "教室");
        List<Long> campusIds = listInstitutionCampusIds(institutionId);
        LambdaQueryWrapper<ClassroomEntity> wrapper = new LambdaQueryWrapper<ClassroomEntity>()
            .orderByAsc(ClassroomEntity::getCampusId)
            .orderByAsc(ClassroomEntity::getClassroomCode);
        if (campusId != null) {
            // 传了别家的校区 id 就什么都别回，而不是把别家的教室列出来
            if (!campusIds.contains(campusId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他机构的教室数据");
            }
            CampusScopeGuard.assertCampus(campusId, "教室");
            wrapper.eq(ClassroomEntity::getCampusId, campusId);
        } else if (!campusIds.isEmpty()) {
            // 不传校区 = 本机构全部校区的汇总；本机构还没建校区就没有教室可列
            wrapper.in(ClassroomEntity::getCampusId, campusIds);
        } else {
            return List.of();
        }
        // 受限角色只能看到被授权校区的教室；空白名单时这里是 1=0
        CampusScopeGuard.apply(wrapper, ClassroomEntity::getCampusId);
        return classroomMapper.selectList(wrapper);
    }

    @Override
    public ClassroomEntity createClassroom(ClassroomCreateRequest request) {
        CampusEntity campus = campusMapper.selectById(request.campusId());
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + request.campusId());
        }
        InstitutionScopeGuard.assertOwn(campus.getInstitutionId(), "教室");
        CampusScopeGuard.assertCampus(campus.getId(), "教室");
        String classroomCode = codeRuleService.resolveForCreate("classroom", request.classroomCode());
        String classroomName = normalize(request.classroomName());
        assertUniqueClassroomCode(request.campusId(), classroomCode, null);
        assertUniqueClassroomName(request.campusId(), classroomName, null);
        if (request.areaSqm() != null && request.areaSqm().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "面积(平方米)不能小于0");
        }

        ClassroomEntity entity = new ClassroomEntity();
        entity.setCampusId(request.campusId());
        entity.setClassroomCode(classroomCode);
        entity.setClassroomName(classroomName);
        String roomType = dictValueValidator.validate("room_type", "教室类型", request.roomType());
        entity.setRoomType(roomType == null ? "normal" : roomType);
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
            log.warn("创建教室撞唯一键(编码={}, 名称={})：{}", classroomCode, classroomName,
                exception.getMostSpecificCause().getMessage());
            throw duplicateKeyException(exception, classroomCode, classroomName);
        }
        return entity;
    }

    @Override
    public ClassroomEntity updateClassroom(Long id, ClassroomUpdateRequest request) {
        ClassroomEntity existing = requireClassroom(id);
        CampusEntity existingCampus = requireCampus(existing.getCampusId());
        InstitutionScopeGuard.assertOwn(existingCampus.getInstitutionId(), "教室");
        CampusScopeGuard.assertCampus(existing.getCampusId(), "教室");
        Long campusId = request.campusId() == null ? existing.getCampusId() : request.campusId();
        if (request.campusId() != null) {
            CampusEntity targetCampus = requireCampus(request.campusId());
            InstitutionScopeGuard.assertOwn(targetCampus.getInstitutionId(), "教室");
            CampusScopeGuard.assertCampus(targetCampus.getId(), "教室");
        }
        if (request.campusId() != null && !Objects.equals(existing.getCampusId(), request.campusId())) {
            CampusEntity campus = campusMapper.selectById(request.campusId());
            if (campus == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + request.campusId());
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
        String roomType = dictValueValidator.validate("room_type", "教室类型", request.roomType());
        if (roomType != null) {
            existing.setRoomType(roomType);
        }
        if (request.floorNo() != null) {
            existing.setFloorNo(normalizeToNull(request.floorNo()));
        }
        if (request.seatCapacity() != null) {
            existing.setSeatCapacity(request.seatCapacity());
        }
        if (request.areaSqm() != null) {
            if (request.areaSqm().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "面积(平方米)不能小于0");
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
            log.warn("更新教室撞唯一键(id={}, 编码={}, 名称={})：{}", id, existing.getClassroomCode(),
                existing.getClassroomName(), exception.getMostSpecificCause().getMessage());
            throw duplicateKeyException(exception, existing.getClassroomCode(), existing.getClassroomName());
        }
        return existing;
    }

    @Override
    public void deleteClassroom(Long id) {
        ClassroomEntity existing = requireClassroom(id);
        CampusEntity campus = requireCampus(existing.getCampusId());
        InstitutionScopeGuard.assertOwn(campus.getInstitutionId(), "教室");
        CampusScopeGuard.assertCampus(existing.getCampusId(), "教室");
        classroomMapper.deleteById(id);
    }

    private CampusEntity requireCampus(Long campusId) {
        CampusEntity campus = campusMapper.selectById(campusId);
        if (campus == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "校区不存在：" + campusId);
        }
        return campus;
    }

    private ClassroomEntity requireClassroom(Long id) {
        ClassroomEntity entity = classroomMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "教室不存在：" + id);
        }
        return entity;
    }

    /**
     * 把唯一键冲突翻成操作员看得懂的提示：按撞到的约束名区分编码/名称，并带上冲突的那个值。
     * 唯一键自 V104 起只约束未删除行，与预检（MyBatis-Plus 自动补 deleted = 0）口径一致；
     * 这里只兜并发窗口，认不出约束名时把两个值都报出来。
     */
    private BusinessException duplicateKeyException(DuplicateKeyException exception, String classroomCode,
                                                    String classroomName) {
        String detail = exception.getMostSpecificCause().getMessage();
        String raw = detail == null ? "" : detail;
        if (raw.contains("uk_jp_classroom_name")) {
            return new BusinessException(ResultCode.BAD_REQUEST, "该校区下已存在教室名称：" + classroomName);
        }
        if (raw.contains("uk_jp_classroom_code")) {
            return new BusinessException(ResultCode.BAD_REQUEST, "该校区下已存在教室编码：" + classroomCode);
        }
        return new BusinessException(ResultCode.BAD_REQUEST,
            "该校区下教室编码 " + classroomCode + " 或名称 " + classroomName + " 已存在");
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下教室编码已存在：" + classroomCode);
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "该校区下教室名称已存在：" + classroomName);
        }
    }

    private List<Long> listInstitutionCampusIds(Long institutionId) {
        if (institutionId == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "未识别当前账号所属机构，无法读取教室");
        }
        return campusMapper.selectList(new LambdaQueryWrapper<CampusEntity>()
                .select(CampusEntity::getId)
                .eq(CampusEntity::getInstitutionId, institutionId))
            .stream()
            .map(CampusEntity::getId)
            .toList();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }
}
