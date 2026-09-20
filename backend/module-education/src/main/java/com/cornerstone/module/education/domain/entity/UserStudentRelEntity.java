package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_user_student_rel")
@Schema(description = "用户与学员档案的关联(小程序学生/家长账号绑定学员)")
public class UserStudentRelEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "用户ID(jp_user_account.id)")
    private Long userId;

    @Schema(description = "学员ID(jp_student.id)")
    private Long studentId;

    @Schema(description = "备注")
    private String remark;
}