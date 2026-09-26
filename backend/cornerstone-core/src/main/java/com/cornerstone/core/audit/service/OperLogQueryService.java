package com.cornerstone.core.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.audit.domain.OperLogEntity;
import com.cornerstone.core.audit.dto.OperLogPageQuery;
import com.cornerstone.core.audit.dto.OperLogView;
import com.cornerstone.core.audit.mapper.OperLogMapper;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.model.PageSortSupport;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 操作日志查询服务(只读,日志只增不改)。
 */
@Service
public class OperLogQueryService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 操作日志列表可排序字段 */
    private static final Map<String, SFunction<OperLogEntity, ?>> OPER_LOG_SORTABLE = Map.of(
        "username", OperLogEntity::getUsername,
        "portal", OperLogEntity::getPortal,
        "operation", OperLogEntity::getOperation,
        "actionType", OperLogEntity::getActionType,
        "success", OperLogEntity::getSuccess,
        "durationMs", OperLogEntity::getDurationMs,
        "clientIp", OperLogEntity::getClientIp,
        "method", OperLogEntity::getMethod,
        "path", OperLogEntity::getPath,
        "createdAt", OperLogEntity::getCreatedAt);

    private final OperLogMapper operLogMapper;

    public OperLogQueryService(OperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    /** 分页查询操作日志,默认按操作时间倒序。 */
    public PageResult<OperLogView> page(OperLogPageQuery query) {
        LambdaQueryWrapper<OperLogEntity> wrapper = new LambdaQueryWrapper<>();
        String username = normalize(query.getUsername());
        if (username != null && !username.isBlank()) {
            wrapper.like(OperLogEntity::getUsername, username);
        }
        if (notBlank(query.getActionType())) {
            wrapper.eq(OperLogEntity::getActionType, query.getActionType());
        }
        if (notBlank(query.getPortal())) {
            wrapper.eq(OperLogEntity::getPortal, query.getPortal());
        }
        if (notBlank(query.getOperation())) {
            wrapper.like(OperLogEntity::getOperation, query.getOperation());
        }
        if (query.getSuccess() != null) {
            wrapper.eq(OperLogEntity::getSuccess, query.getSuccess());
        }
        LocalDateTime begin = parseTime(query.getBeginTime());
        if (begin != null) {
            wrapper.ge(OperLogEntity::getCreatedAt, begin);
        }
        LocalDateTime end = parseTime(query.getEndTime());
        if (end != null) {
            wrapper.le(OperLogEntity::getCreatedAt, end);
        }
        if (!PageSortSupport.apply(wrapper, query, OPER_LOG_SORTABLE, OperLogEntity::getId)) {
            wrapper.orderByDesc(OperLogEntity::getCreatedAt);
        }

        Page<OperLogEntity> page = operLogMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toView).toList());
    }

    private OperLogView toView(OperLogEntity entity) {
        return new OperLogView(
            entity.getId(),
            entity.getTraceId(),
            entity.getUserId(),
            entity.getUsername(),
            entity.getOrgId(),
            entity.getPortal(),
            entity.getOperation(),
            entity.getActionType(),
            entity.getMethod(),
            entity.getPath(),
            entity.getClientIp(),
            entity.getUserAgent(),
            entity.getParams(),
            entity.getErrorMsg(),
            entity.getSuccess(),
            entity.getDurationMs(),
            entity.getCreatedAt()
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}