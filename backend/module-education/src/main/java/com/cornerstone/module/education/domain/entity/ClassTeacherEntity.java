package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_class_teacher")
@Schema(description = "班级教师关系")
public class ClassTeacherEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "班级ID")
    private Long classId;

    @Schema(description = "教师ID")
    private Long teacherId;

    @Schema(description = "教师角色字典(teacher_role:lead=主讲,assistant=助教,substitute=代课)")
    private String teacherRole;

    @Schema(description = "是否主讲")
    private Boolean isPrimary;

    @Schema(description = "加入日期")
    private LocalDate joinedAt;

    @Schema(description = "离开日期")
    private LocalDate leftAt;

    @Schema(description = "状态(active=启用,inactive=停用;停用后不计入班级任课教师)",
            allowableValues = {"active", "inactive"})
    private String status;

    @Schema(description = "备注")
    private String remark;
}
