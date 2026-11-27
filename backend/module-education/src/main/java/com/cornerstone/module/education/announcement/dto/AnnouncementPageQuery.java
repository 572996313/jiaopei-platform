package com.cornerstone.module.education.announcement.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 公告分页查询参数，GET /api/education/announcements/page 使用。
 * 机构条件不在这里：一律取登录态所属机构。
 */
@Schema(description = "公告分页查询参数")
public class AnnouncementPageQuery extends PageQuery {

    @Schema(description = "公告状态(draft=草稿,published=已发布,recalled=已撤回),精确匹配",
        allowableValues = {"draft", "published", "recalled"})
    private String status;

    @Schema(description = "公告类型字典值(announcement_type),精确匹配")
    private String typeCode;

    @Schema(description = "接收对象(teacher/parent/account),按范围的包含关系匹配")
    private String audienceCode;

    @Schema(description = "关键字,模糊匹配标题")
    private String keyword;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getAudienceCode() {
        return audienceCode;
    }

    public void setAudienceCode(String audienceCode) {
        this.audienceCode = audienceCode;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
