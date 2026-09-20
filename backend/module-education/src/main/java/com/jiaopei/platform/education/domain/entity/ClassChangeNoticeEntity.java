package com.jiaopei.platform.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_change_notice")
@Schema(description = "班级变更通知")
public class ClassChangeNoticeEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID")
    private Long campusId;

    @Schema(description = "班级ID")
    private Long classId;

    @Schema(description = "来源类型(ENROLLMENT/RENEWAL/UPGRADE/OTHER)")
    private String sourceType;

    @Schema(description = "来源业务单据ID")
    private Long sourceId;

    @Schema(description = "通知类型(STOP=停课,MAKEUP=补课,ADJUST=调整,RESCHEDULE=改期,TRANSFER=转班)")
    private String noticeType;

    @Schema(description = "通知级别(INFO/WARNING/URGENT)")
    private String noticeLevel;

    @Schema(description = "通知状态(PENDING=待处理,RESOLVED=已处理,CLOSED=已关闭)")
    private String noticeStatus;

    @Schema(description = "通知内容")
    private String noticeMessage;

    @Schema(description = "建议处置动作")
    private String suggestedAction;

    @Schema(description = "处理完成时间")
    private LocalDateTime resolvedAt;

    @Schema(description = "处理人ID")
    private Long resolvedBy;

    @Schema(description = "备注")
    private String remark;
}
