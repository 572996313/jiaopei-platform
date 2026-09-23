package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_student")
@Schema(description = "学员")
public class StudentEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID")
    private Long campusId;

    @Schema(description = "归属用户ID(学生端账号绑定自己时写入,一个账号唯一归属一个档案)")
    private Long userId;

    @Schema(description = "学员编号")
    private String studentNo;

    @Schema(description = "学员姓名")
    private String studentName;

    @Schema(description = "性别(MALE=男,FEMALE=女,UNKNOWN=未知)", allowableValues = {"MALE", "FEMALE", "UNKNOWN"})
    private String gender;

    @Schema(description = "出生日期")
    private LocalDate birthday;

    @Schema(description = "监护人姓名")
    private String guardianName;

    @Schema(description = "监护人联系电话")
    private String guardianPhone;

    @Schema(description = "状态(studying=在读,suspended=停课,graduated=已结业,withdrawn=已退学)", allowableValues = {"studying", "suspended", "graduated", "withdrawn"})
    private String status;

    @Schema(description = "入学日期")
    private LocalDate enrolledAt;

    @Schema(description = "备注")
    private String remark;
}
