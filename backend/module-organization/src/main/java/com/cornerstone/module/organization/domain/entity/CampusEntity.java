package com.cornerstone.module.organization.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_campus")
public class CampusEntity extends BaseEntity {

    private Long institutionId;
    private String campusCode;
    private String campusName;
    @Schema(description = "校区类型字典(campus_type:direct=直营,franchise=加盟,partner=合作)")
    private String campusType;
    private String phone;
    private String principalName;
    private String provinceCode;
    private String cityCode;
    private String districtCode;
    private String address;
    private Integer capacityLimit;
    private String status;
    private LocalDate openedAt;
    private Integer sortNo;
    private String remark;
}
