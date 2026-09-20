package com.cornerstone.module.education.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_teacher")
@Schema(description = "教师")
public class TeacherEntity extends BaseEntity {

    @Schema(description = "机构ID(多租户隔离)")
    private Long institutionId;

    @Schema(description = "校区ID")
    private Long campusId;

    @Schema(description = "教师编号")
    private String teacherCode;

    @Schema(description = "教师姓名")
    private String teacherName;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "性别(MALE=男,FEMALE=女,UNKNOWN=未知)", allowableValues = {"MALE", "FEMALE", "UNKNOWN"})
    private String gender;

    @Schema(description = "职称(如 初级/中级/高级)")
    private String title;

    @Schema(description = "雇佣类型(FULL_TIME=全职,PART_TIME=兼职,OUTSOURCE=外聘)", allowableValues = {"FULL_TIME", "PART_TIME", "OUTSOURCE"})
    private String employmentType;

    @Schema(description = "状态(ENABLED=在职,DISABLED=离职)", allowableValues = {"ENABLED", "DISABLED"})
    private String status;

    @Schema(description = "入职日期")
    private LocalDate hiredAt;

    @Schema(description = "备注")
    private String remark;
}
