package com.cornerstone.core.file.storage;

import java.io.InputStream;

/**
 * 文件存储 SPI。上层只认对象键，不关心底层是 MinIO 还是本地磁盘；
 * 将来换实现（S3、OSS、直传）不影响 fileId 契约。
 */
public interface FileStorage {

    /** 实现名，用于启动日志与排障。 */
    String providerName();

    /**
     * 写入对象。调用方负责关闭 {@code inputStream}。
     *
     * @param objectKey   后端生成的对象键，不含任何用户输入
     * @param sizeBytes   对象字节数，必须准确
     * @param contentType MIME 类型，由服务端按扩展名推导，不采信客户端
     */
    void put(String objectKey, InputStream inputStream, long sizeBytes, String contentType);

    /** 读取对象字节流，调用方负责关闭。 */
    InputStream get(String objectKey);

    /** 删除对象；对象不存在视为成功。 */
    void delete(String objectKey);
}
