package com.cornerstone.core.file.service.support;

import com.cornerstone.core.file.service.FileService;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤儿文件定时清理。
 *
 * <p>定时任务开关由 {@code file.orphan.retention_hours} 控制：配成 0 或负数即停止回收，
 * 现场需要临时关闭时改系统参数即可，不用重启。</p>
 */
@Component
public class FileCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupJob.class);

    private final FileService fileService;

    public FileCleanupJob(FileService fileService) {
        this.fileService = fileService;
    }

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanupOrphans() {
        LocalTime startedAt = LocalTime.now();
        int removed = fileService.cleanupOrphans();
        log.info("孤儿文件清理完成：删除 {} 个，开始时间 {}", removed, startedAt);
    }
}
