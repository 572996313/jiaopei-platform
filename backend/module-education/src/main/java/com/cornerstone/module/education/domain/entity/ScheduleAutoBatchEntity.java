package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_schedule_auto_batch")
@Schema(description = "自动排课批次(按缺口补排的执行记录与整批撤销依据)")
public class ScheduleAutoBatchEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID(批次按校区发起)")
    private Long campusId;

    @Schema(description = "学期ID(本批发起时所选学期,只作报表筛选范围;课次学期取班级自己的学期)")
    private Long termId;

    @Schema(description = "补排窗口起始日期")
    private LocalDate startDate;

    @Schema(description = "补排窗口截止日期")
    private LocalDate endDate;

    @Schema(description = "本次纳入的班级ID列表(JSON数组,空=该校区全部待补班级)")
    private String classIdsJson;

    @Schema(description = "本批实际生效的择优策略(JSON)")
    private String strategyJson;

    @Schema(description = "待补节数(进入引擎的缺口合计,整班退回的班级不在这里)")
    private Integer requestedCount;

    @Schema(description = "本批生成的课次数")
    private Integer generatedCount;

    @Schema(description = "没补上的节数(待补-已生成;一条原因可能对应多节)")
    private Integer unscheduledCount;

    @Schema(description = "没补上的明细(JSON数组[{classId,className,weekNo,reason}])")
    private String failureJson;

    @Schema(description = "批次状态(completed=已执行,rolled_back=已撤销)", allowableValues = {"completed", "rolled_back"})
    private String status;

    @Schema(description = "撤销时间(未撤销为空)")
    private LocalDateTime rolledBackAt;

    @Schema(description = "备注")
    private String remark;
}
