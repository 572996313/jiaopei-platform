package com.jiaopei.platform.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiaopei.platform.common.api.ResultCode;
import com.jiaopei.platform.common.exception.BusinessException;
import com.jiaopei.platform.system.domain.SystemDictItemEntity;
import com.jiaopei.platform.system.domain.SystemDictTypeEntity;
import com.jiaopei.platform.system.dto.DictItemCreateRequest;
import com.jiaopei.platform.system.dto.DictItemUpdateRequest;
import com.jiaopei.platform.system.dto.DictItemView;
import com.jiaopei.platform.system.dto.DictTypeCreateRequest;
import com.jiaopei.platform.system.dto.DictTypeUpdateRequest;
import com.jiaopei.platform.system.dto.DictTypeView;
import com.jiaopei.platform.system.mapper.SystemDictItemMapper;
import com.jiaopei.platform.system.mapper.SystemDictTypeMapper;
import com.jiaopei.platform.system.service.SystemDictService;
import java.util.List;
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "Dict type already exists: " + entity.getTypeCode());
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
            throw new BusinessException(ResultCode.BAD_REQUEST, "Dict item already exists: " + entity.getItemCode());
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
        itemMapper.deleteById(id);
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
            throw new BusinessException(ResultCode.NOT_FOUND, "Dict type does not exist: " + id);
        }
        return entity;
    }

    private SystemDictTypeEntity requireTypeByCode(String typeCode) {
        SystemDictTypeEntity entity = typeMapper.selectOne(new LambdaQueryWrapper<SystemDictTypeEntity>()
            .eq(SystemDictTypeEntity::getTypeCode, normalize(typeCode)));
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Dict type does not exist: " + typeCode);
        }
        return entity;
    }

    private SystemDictItemEntity requireItem(Long id) {
        SystemDictItemEntity entity = itemMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Dict item does not exist: " + id);
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
