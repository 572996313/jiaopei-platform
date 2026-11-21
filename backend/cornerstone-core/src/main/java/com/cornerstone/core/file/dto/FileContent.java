package com.cornerstone.core.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.InputStream;

/**
 * 文件字节流与下载所需元信息。控制器负责把它转成 {@code ResponseEntity<Resource>}，
 * 不要在这里预读成全量 byte[]（大文件会占满堆）。
 */
@Schema(description = "文件字节流")
public record FileContent(
    @Schema(description = "文件字节流，调用方负责关闭") InputStream stream,
    @Schema(description = "原始文件名") String fileName,
    @Schema(description = "MIME类型") String contentType,
    @Schema(description = "文件字节数") long sizeBytes
) {
}
