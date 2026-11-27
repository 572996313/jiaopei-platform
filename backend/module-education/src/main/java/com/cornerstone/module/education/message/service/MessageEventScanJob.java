package com.cornerstone.module.education.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.module.education.message.domain.entity.MsgEventEntity;
import com.cornerstone.module.education.message.domain.entity.MsgEventTypeEntity;
import com.cornerstone.module.education.message.mapper.MsgEventLogMapper;
import com.cornerstone.module.education.message.mapper.MsgEventMapper;
import com.cornerstone.module.education.message.mapper.MsgEventReadMapper;
import com.cornerstone.module.education.message.mapper.MsgEventTypeMapper;
import com.cornerstone.module.education.message.support.MessageTypeRules;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 事件巡检与保留期清理。
 *
 * <p>调度开关复用底座 {@code @EnableScheduling}（见 FileStorageConfiguration）。
 * fixedDelay 保证上一轮跑完才排下一轮，不会重叠；按单实例部署设计，多实例同时跑
 * 巡检虽然幂等，但会产生重复扫描成本，扩容时需要改为分布式锁。</p>
 */
@Component
public class MessageEventScanJob {

    private static final Logger log = LoggerFactory.getLogger(MessageEventScanJob.class);

    private final MsgEventTypeMapper typeMapper;
    private final MsgEventMapper eventMapper;
    private final MsgEventReadMapper readMapper;
    private final MsgEventLogMapper logMapper;
    private final MessageEventScanService scanService;

    public MessageEventScanJob(MsgEventTypeMapper typeMapper,
                               MsgEventMapper eventMapper,
                               MsgEventReadMapper readMapper,
                               MsgEventLogMapper logMapper,
                               MessageEventScanService scanService) {
        this.typeMapper = typeMapper;
        this.eventMapper = eventMapper;
        this.readMapper = readMapper;
        this.logMapper = logMapper;
        this.scanService = scanService;
    }

    @Scheduled(fixedDelayString = "${message.event.scan-interval-ms:60000}")
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        List<MsgEventTypeEntity> types = typeMapper.selectList(new LambdaQueryWrapper<MsgEventTypeEntity>()
            .eq(MsgEventTypeEntity::getSourceMode, MessageTypeRules.SOURCE_MODE_SCAN)
            .eq(MsgEventTypeEntity::getEnabled, true)
            .orderByAsc(MsgEventTypeEntity::getSortNo));
        for (MsgEventTypeEntity type : types) {
            if (isNotDue(type, now)) {
                continue;
            }
            try {
                int matched = scanService.scanType(type, now);
                log.info("事件巡检完成：type={}, 命中={}", type.getTypeCode(), matched);
            } catch (Exception exception) {
                // 失败原因已由 scanService 写回 last_error；这里只保证不中断后续类型
                log.warn("事件巡检失败：type={}, 原因={}", type.getTypeCode(), exception.getMessage());
            }
        }
    }

    private boolean isNotDue(MsgEventTypeEntity type, LocalDateTime now) {
        if (type.getLastRunAt() == null) {
            return false;
        }
        int interval = type.getIntervalMinutes() == null ? 5 : Math.max(1, type.getIntervalMinutes());
        return type.getLastRunAt().plusMinutes(interval).isAfter(now);
    }

    /**
     * 保留期清理：只清理已消解/已完成的事件，active/open 不删。
     *
     * <p>事件、已读、时间线一起**物理删除**（不是逻辑删除）：唯一键含 deleted 列，
     * 逻辑删除每个键只能留一条墓碑，同一去重键（如去年同一课次）再次过期时会撞唯一键。
     * 过期事件是历史数据，没有重建同键的管理入口，按 AGENTS「逻辑删除唯一键收口」物理删除。
     * 清理与巡检分开调度，避免白天跑清理影响业务库。</p>
     */
    @Scheduled(cron = "${message.event.cleanup-cron:0 30 3 * * ?}")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        List<MsgEventTypeEntity> types = typeMapper.selectList(null);
        int deleted = 0;
        for (MsgEventTypeEntity type : types) {
            int retentionDays = type.getRetentionDays() == null ? 90 : Math.max(1, type.getRetentionDays());
            LocalDateTime cutoff = now.minusDays(retentionDays);
            List<Long> ids = eventMapper.selectList(new LambdaQueryWrapper<MsgEventEntity>()
                    .eq(MsgEventEntity::getTypeCode, type.getTypeCode())
                    .lt(MsgEventEntity::getOccurredAt, cutoff)
                    .in(MsgEventEntity::getStatus, List.of(
                        MessageTypeRules.STATUS_RESOLVED,
                        MessageTypeRules.STATUS_DONE,
                        MessageTypeRules.STATUS_CLOSED,
                        "expired"))
                    .select(MsgEventEntity::getId))
                .stream()
                .map(MsgEventEntity::getId)
                .toList();
            if (ids.isEmpty()) {
                continue;
            }
            readMapper.physicalDeleteByEventIds(ids);
            logMapper.physicalDeleteByEventIds(ids);
            eventMapper.physicalDeleteByIds(ids);
            deleted += ids.size();
        }
        if (deleted > 0) {
            log.info("事件保留期清理完成：清理 {} 条已结束事件", deleted);
        }
    }
}
