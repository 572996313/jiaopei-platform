package com.cornerstone.core.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.domain.SystemDictItemEntity;
import com.cornerstone.core.system.domain.SystemDictTypeEntity;
import com.cornerstone.core.system.dto.DictItemCreateRequest;
import com.cornerstone.core.system.dto.DictItemUpdateRequest;
import com.cornerstone.core.system.dto.DictItemView;
import com.cornerstone.core.system.dto.DictOptionGroupView;
import com.cornerstone.core.system.dto.DictTypeCreateRequest;
import com.cornerstone.core.system.dto.DictTypeUpdateRequest;
import com.cornerstone.core.system.dto.DictTypeView;
import com.cornerstone.core.system.mapper.SystemDictItemMapper;
import com.cornerstone.core.system.mapper.SystemDictTypeMapper;
import com.cornerstone.core.system.service.SystemDictService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemDictServiceImpl implements SystemDictService {

    private static final String ACTIVE = "active";

    private final SystemDictTypeMapper typeMapper;
    private final SystemDictItemMapper itemMapper;

    public SystemDictServiceImpl(SystemDictTypeMapper typeMapper, SystemDictItemMapper itemMapper) {
        this.typeMapper = typeMapper;
        this.itemMapper = itemMapper;
    }

    @Override
    public List<DictTypeView> listTypes() {
        return typeMapper.selectList(new LambdaQueryWrapper<SystemDictTypeEntity>()
                .orderByAsc(SystemDictTypeEntity::getTypeCode))
            .stream()
            .map(this::toTypeView)
            .toList();
    }

    @Override
    public List<DictItemView> listItems(String typeCode) {
        SystemDictTypeEntity type = requireTypeByCode(typeCode);
        return itemMapper.selectList(new LambdaQueryWrapper<SystemDictItemEntity>()
                .eq(SystemDictItemEntity::getTypeId, type.getId())
                .orderByAsc(SystemDictItemEntity::getSortNo)
                .orderByAsc(SystemDictItemEntity::getItemCode))
            .stream()
            .map(this::toItemView)
            .toList();
    }

    @Override
    public List<DictOptionGroupView> listOptions(List<String> typeCodes) {
        List<SystemDictTypeEntity> types = typeMapper.selectList(new LambdaQueryWrapper<SystemDictTypeEntity>()
                .eq(SystemDictTypeEntity::getStatus, ACTIVE)
                .orderByAsc(SystemDictTypeEntity::getTypeCode));
        if (typeCodes != null && !typeCodes.isEmpty()) {
            types = types.stream()
                .filter(type -> typeCodes.contains(type.getTypeCode()))
                .toList();
        }
        if (types.isEmpty()) {
            return List.of();
        }
        List<Long> typeIds = types.stream().map(SystemDictTypeEntity::getId).toList();
        Map<Long, List<DictItemView>> itemsByTypeId = itemMapper.selectList(new LambdaQueryWrapper<SystemDictItemEntity>()
                .in(SystemDictItemEntity::getTypeId, typeIds)
                .eq(SystemDictItemEntity::getStatus, ACTIVE)
                .orderByAsc(SystemDictItemEntity::getSortNo)
                .orderByAsc(SystemDictItemEntity::getItemCode))
            .stream()
            .map(this::toItemView)
            .collect(Collectors.groupingBy(DictItemView::typeId));
        return types.stream()
            .map(type -> new DictOptionGroupView(
                type.getTypeCode(),
                type.getTypeName(),
                itemsByTypeId.getOrDefault(type.getId(), List.of())))
            .toList();
    }

    @Override
    @Transactional
    public DictTypeView createType(DictTypeCreateRequest request) {
        SystemDictTypeEntity entity = new SystemDictTypeEntity();
        entity.setTypeCode(normalize(request.typeCode()));
        entity.setTypeName(normalize(request.typeName()));
        entity.setStatus(defaultStatus(request.status()));
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            typeMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "字典类型已存在：" + entity.getTypeCode());
        }
        return toTypeView(entity);
    }

    @Override
    @Transactional
    public DictTypeView updateType(Long id, DictTypeUpdateRequest request) {
        SystemDictTypeEntity entity = requireType(id);
        entity.setTypeName(normalize(request.typeName()));
        entity.setStatus(normalize(request.status()));
        entity.setRemark(request.remark());
        typeMapper.updateById(entity);
        return toTypeView(entity);
    }

    @Override
    @Transactional
    public void deleteType(Long id) {
        SystemDictTypeEntity entity = requireType(id);
        Long itemCount = itemMapper.selectCount(new LambdaQueryWrapper<SystemDictItemEntity>()
            .eq(SystemDictItemEntity::getTypeId, id));
        if (itemCount != null && itemCount > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "字典类型下仍存在条目，请先删除条目：" + entity.getTypeCode());
        }
        typeMapper.physicalDeleteById(id);
    }

    @Override
    @Transactional
    public DictItemView createItem(DictItemCreateRequest request) {
        requireType(request.typeId());
        SystemDictItemEntity entity = new SystemDictItemEntity();
        entity.setTypeId(request.typeId());
        entity.setItemCode(normalize(request.itemCode()));
        entity.setItemName(normalize(request.itemName()));
        entity.setItemValue(normalize(request.itemValue()));
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus(defaultStatus(request.status()));
        entity.setRemark(request.remark());
        entity.setDeleted(Boolean.FALSE);
        try {
            itemMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "字典条目已存在：" + entity.getItemCode());
        }
        return toItemView(entity);
    }

    @Override
    @Transactional
    public DictItemView updateItem(Long id, DictItemUpdateRequest request) {
        SystemDictItemEntity entity = requireItem(id);
        entity.setItemName(normalize(request.itemName()));
        entity.setItemValue(normalize(request.itemValue()));
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus(normalize(request.status()));
        entity.setRemark(request.remark());
        itemMapper.updateById(entity);
        return toItemView(entity);
    }

    @Override
    @Transactional
    public void deleteItem(Long id) {
        requireItem(id);
        itemMapper.physicalDeleteById(id);
    }

    private DictTypeView toTypeView(SystemDictTypeEntity entity) {
        return new DictTypeView(entity.getId(), entity.getTypeCode(), entity.getTypeName(), entity.getStatus(), entity.getRemark());
    }

    private DictItemView toItemView(SystemDictItemEntity entity) {
        return new DictItemView(
            entity.getId(),
            entity.getTypeId(),
            entity.getItemCode(),
            entity.getItemName(),
            entity.getItemValue(),
            entity.getSortNo(),
            entity.getStatus(),
            entity.getRemark()
        );
    }

    private SystemDictTypeEntity requireType(Long id) {
        SystemDictTypeEntity entity = typeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典类型不存在：" + id);
        }
        return entity;
    }

    private SystemDictTypeEntity requireTypeByCode(String typeCode) {
        SystemDictTypeEntity entity = typeMapper.selectOne(new LambdaQueryWrapper<SystemDictTypeEntity>()
            .eq(SystemDictTypeEntity::getTypeCode, normalize(typeCode)));
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典类型不存在：" + typeCode);
        }
        return entity;
    }

    private SystemDictItemEntity requireItem(Long id) {
        SystemDictItemEntity entity = itemMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典条目不存在：" + id);
        }
        return entity;
    }

    private String defaultStatus(String status) {
        String normalized = normalize(status);
        return normalized == null || normalized.isBlank() ? ACTIVE : normalized;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
