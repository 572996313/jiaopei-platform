package com.cornerstone.core.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 文件元数据视图。
 *
 * <p>只下发 fileId 与元数据，不下发对象键与桶名：前端拿 fileId 走
 * {@code GET /api/system/files/{fileId}/content} 取字节。业务 DTO 保存的也是 fileId。</p>
 */
@Schema(description = "文件元数据视图")
public record FileView(
    @Schema(description = "文件ID(业务表保存这个值)") Long fileId,
    @Schema(description = "原始文件名") String fileName,
    @Schema(description = "扩展名(小写,不含点)") String extension,
    @Schema(description = "MIME类型") String contentType,
    @Schema(description = "文件字节数") Long sizeBytes,
    @Schema(description = "业务类型码") String bizType,
    @Schema(description = "业务主键") Long bizId,
    @Schema(description = "上传时间") LocalDateTime createdAt
) {
}
