package com.cornerstone.core.file.storage;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;

/**
 * MinIO 存储实现。桶保持私有，读写在服务端完成，桶地址不需要对浏览器可达。
 */
public class MinioFileStorage implements FileStorage {

    private final MinioClient client;

    private final String bucket;

    public MinioFileStorage(String endpoint, String accessKey, String secretKey, String region, String bucket) {
        this.client = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .region(region)
            .build();
        this.bucket = bucket;
    }

    /** 启动期确认桶存在，避免第一次上传时才发现配置错了。 */
    public void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                "无法连接对象存储或无法创建桶 " + bucket + "，请检查 MINIO_ENDPOINT / 凭据配置", exception);
        }
    }

    @Override
    public String providerName() {
        return "minio";
    }

    @Override
    public void put(String objectKey, InputStream inputStream, long sizeBytes, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(inputStream, sizeBytes, -1)
                .contentType(contentType)
                .build());
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件写入对象存储失败，请稍后重试");
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在或已被清理");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "文件删除失败，请稍后重试");
        }
    }
}
