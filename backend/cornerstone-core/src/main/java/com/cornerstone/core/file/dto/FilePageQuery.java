package com.cornerstone.core.file.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 文件管理分页查询参数，{@code GET /api/system/files/page} 使用。
 */
@Schema(description = "文件管理分页查询参数")
public class FilePageQuery extends PageQuery {

    @Schema(description = "关键字，模糊匹配原始文件名")
    private String keyword;

    @Schema(description = "业务类型码，精确匹配")
    private String bizType;

    @Schema(description = "业务主键，精确匹配")
    private Long bizId;

    @Schema(description = "上传时间起(含)，格式 yyyy-MM-ddTHH:mm:ss")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdFrom;

    @Schema(description = "上传时间止(含)，格式 yyyy-MM-ddTHH:mm:ss")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdTo;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
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

    public LocalDateTime getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(LocalDateTime createdFrom) {
        this.createdFrom = createdFrom;
    }

    public LocalDateTime getCreatedTo() {
        return createdTo;
    }

    public void setCreatedTo(LocalDateTime createdTo) {
        this.createdTo = createdTo;
    }
}
