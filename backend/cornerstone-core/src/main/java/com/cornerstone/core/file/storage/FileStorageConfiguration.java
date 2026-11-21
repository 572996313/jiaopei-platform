package com.cornerstone.core.file.storage;

import com.cornerstone.core.config.properties.CornerstoneProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文件存储实现装配。
 *
 * <p>{@code @EnableScheduling} 放在这里：孤儿文件清理需要定时任务，仓库此前没有开调度；
 * 只注册一个单线程调度器，下游复用底座即可，不需要各自再开。</p>
 */
@Configuration
@EnableScheduling
public class FileStorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FileStorageConfiguration.class);

    @Bean
    @ConditionalOnProperty(prefix = "cornerstone.file", name = "provider", havingValue = "minio")
    public FileStorage minioFileStorage(CornerstoneProperties properties) {
        CornerstoneProperties.FileStorage config = properties.getFile();
        MinioFileStorage storage = new MinioFileStorage(
            config.getEndpoint(), config.getAccessKey(), config.getSecretKey(),
            config.getRegion(), config.getBucket());
        storage.ensureBucket();
        log.info("文件存储已启用：MinIO，桶={}，地址={}", config.getBucket(), config.getEndpoint());
        return storage;
    }

    @Bean
    @ConditionalOnProperty(prefix = "cornerstone.file", name = "provider",
        havingValue = "local", matchIfMissing = true)
    public FileStorage localFileStorage(CornerstoneProperties properties) {
        String localDir = properties.getFile().getLocalDir();
        log.warn("当前使用本地磁盘存储（{}）：仅限开发环境，生产请设置 FILE_STORAGE_PROVIDER=minio", localDir);
        return new LocalFileStorage(localDir);
    }
}
