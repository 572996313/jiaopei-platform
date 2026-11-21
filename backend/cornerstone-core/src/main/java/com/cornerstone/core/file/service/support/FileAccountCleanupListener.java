package com.cornerstone.core.file.service.support;

import com.cornerstone.core.auth.event.UserAccountDeregisteredEvent;
import com.cornerstone.core.file.service.FileService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 账号注销时清空文件的上传人归属。
 *
 * <p>文件是机构资产，不随上传人注销删除；这里只把 {@code uploaded_by} 置空，
 * 避免留下指向已不存在账号的悬空引用。</p>
 */
@Component
public class FileAccountCleanupListener {

    private final FileService fileService;

    public FileAccountCleanupListener(FileService fileService) {
        this.fileService = fileService;
    }

    @EventListener
    public void onUserAccountDeregistered(UserAccountDeregisteredEvent event) {
        fileService.clearUploader(event.userId());
    }
}
