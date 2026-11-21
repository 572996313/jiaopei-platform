package com.cornerstone.core.file.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文件元数据。
 *
 * <p>业务表只保存本表的 {@code id}（对外的 fileId），不保存对象键、桶名与 URL：
 * 换桶/换域名/上 CDN 时只需改配置，不需要回写任何业务数据。</p>
 */
@TableName("sys_file")
@Schema(description = "文件元数据")
public class FileEntity extends BaseEntity {

    @Schema(description = "所属机构ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long institutionId;

    @Schema(description = "对象存储桶名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bucket;

    @Schema(description = "对象键(后端生成:机构ID/年/月/UUID.扩展名)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String objectKey;

    @Schema(description = "原始文件名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String originalName;

    @Schema(description = "扩展名(小写,不含点)")
    private String extension;

    @Schema(description = "MIME类型")
    private String contentType;

    @Schema(description = "文件字节数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sizeBytes;

    @Schema(description = "内容摘要(SHA-256,预留秒传/去重)")
    private String sha256;

    @Schema(description = "业务类型码(调用方约定,如student_photo)")
    private String bizType;

    @Schema(description = "业务主键(为空视为未绑定,按孤儿清理)")
    private Long bizId;

    @Schema(description = "上传人ID")
    private Long uploadedBy;

    public Long getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(Long institutionId) {
        this.institutionId = institutionId;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
