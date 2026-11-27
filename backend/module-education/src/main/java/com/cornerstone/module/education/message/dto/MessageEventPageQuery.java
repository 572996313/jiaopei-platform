package com.cornerstone.module.education.message.dto;

import com.cornerstone.core.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 事件流分页查询参数。
 *
 * <p>机构条件不在这里：一律取登录态所属机构。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "事件流分页查询参数")
public class MessageEventPageQuery extends PageQuery {

    @Schema(description = "事件类型码,精确匹配")
    private String typeCode;

    @Schema(description = "分类(message_category),精确匹配")
    private String category;

    @Schema(description = "校区ID")
    private Long campusId;

    @Schema(description = "班级锚点ID")
    private Long classId;

    @Schema(description = "学员锚点ID")
    private Long studentId;

    @Schema(description = "教师锚点ID")
    private Long teacherId;

    @Schema(description = "指派人账号ID")
    private Long assigneeUserId;

    @Schema(description = "状态；all=全部，其余精确匹配(active/resolved/open/in_progress/done/closed)")
    private String status;

    @Schema(description = "只看未结束(active/open/in_progress)；用于铃铛与默认工作视图，与 status 互斥，status 优先")
    private Boolean openOnly;

    @Schema(description = "级别(info/warning/critical)")
    private String level;

    @Schema(description = "关键字，模糊匹配标题与正文")
    private String keyword;

    @Schema(description = "发生时间起(含)")
    private LocalDate dateFrom;

    @Schema(description = "发生时间止(含)")
    private LocalDate dateTo;

    @Schema(description = "只看未读")
    private Boolean unreadOnly;

    @Schema(description = "只看我关心的：命中我的订阅规则或指派给我；全部可见事件仍可通过 stream 查看")
    private Boolean mine;
}
