package com.cornerstone.core.file.storage;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储实现，只用于开发环境与没有对象存储时的兜底。
 *
 * <p>生产环境必须用 MinIO：容器重建会丢掉本地磁盘上的文件，且多实例之间不共享。</p>
 */
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(String localDir) {
        this.root = Paths.get(localDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建本地文件目录：" + root, exception);
        }
    }

    @Override
    public String providerName() {
        return "local";
    }

    @Override
    public void put(String objectKey, InputStream inputStream, long sizeBytes, String contentType) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件写入本地存储失败，请稍后重试");
        }
    }

    @Override
    public InputStream get(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在或已被清理");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件读取失败，请稍后重试");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件删除失败，请稍后重试");
        }
    }

    /** 解析并归一化路径，任何越出根目录的键都直接拒绝（防路径穿越）。 */
    private Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法的对象键");
        }
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "非法的对象键");
        }
        return target;
    }
}
