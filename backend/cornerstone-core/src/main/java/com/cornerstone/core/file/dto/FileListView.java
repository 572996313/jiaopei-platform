package com.cornerstone.core.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 文件管理页列表行。比 {@link FileView} 多出机构与上传人，供运维排查使用；
 * 业务侧不需要这些字段，不要拿它当通用返回。
 */
@Schema(description = "文件管理列表视图")
public record FileListView(
    @Schema(description = "文件ID") Long fileId,
    @Schema(description = "原始文件名") String fileName,
    @Schema(description = "扩展名(小写,不含点)") String extension,
    @Schema(description = "MIME类型") String contentType,
    @Schema(description = "文件字节数") Long sizeBytes,
    @Schema(description = "业务类型码") String bizType,
    @Schema(description = "业务主键") Long bizId,
    @Schema(description = "所属机构ID") Long institutionId,
    @Schema(description = "上传人ID") Long uploadedBy,
    @Schema(description = "上传时间") LocalDateTime createdAt
) {
}
