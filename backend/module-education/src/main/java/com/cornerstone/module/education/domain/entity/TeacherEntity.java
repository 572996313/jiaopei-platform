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

    @Schema(description = "关联登录账号ID(sys_user_account.id),NULL=尚未开通或未绑定账号")
    private Long userId;

    @Schema(description = "教师编号")
    private String teacherCode;

    @Schema(description = "教师姓名")
    private String teacherName;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "性别字典(gender:male=男,female=女,unknown=未知)", allowableValues = {"male", "female", "unknown"})
    private String gender;

    @Schema(description = "职称(如 初级/中级/高级)")
    private String title;

    @Schema(description = "用工类型字典(employment_type:full_time=全职,part_time=兼职,contract=外聘)", allowableValues = {"full_time", "part_time", "contract"})
    private String employmentType;

    @Schema(description = "教师状态字典(teacher_status:active=在职,suspended=暂停,left=离职)", allowableValues = {"active", "suspended", "left"})
    private String status;

    @Schema(description = "入职日期")
    private LocalDate hiredAt;

    @Schema(description = "备注")
    private String remark;
}
