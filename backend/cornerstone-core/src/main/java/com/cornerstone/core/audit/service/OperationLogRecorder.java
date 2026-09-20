package com.cornerstone.core.audit.service;

import com.cornerstone.core.audit.domain.OperLogEntity;
import com.cornerstone.core.audit.mapper.OperLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作留痕落库服务。异步执行(Spring @Async,线程池 operationLogExecutor),
 * 在业务事务之外触发:日志库不可达 / 写入失败只打印告警,
 * 绝不影响业务响应与主事务。
 */
@Service
public class OperationLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(OperationLogRecorder.class);

    private final OperLogMapper operLogMapper;

    public OperationLogRecorder(OperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    /** 异步记录一条操作日志。调用方(切面)无需捕获异常,此处已隔离。 */
    @Async("operationLogExecutor")
    public void record(OperLogEntity entity) {
        try {
            operLogMapper.insert(entity);
        } catch (Exception e) {
            log.warn("操作留痕写入失败(已忽略,不影响业务):{}", e.getMessage());
        }
    }
}