package com.jiaopei.platform.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiaopei.platform.audit.domain.OperLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作留痕日志 Mapper,绑定审计 SqlSessionFactory(auditSqlSessionFactory),
 * 由 AuditDataSourceConfig 的 @MapperScan 扫描,与业务 Mapper 隔离。
 */
@Mapper
public interface OperLogMapper extends BaseMapper<OperLogEntity> {
}