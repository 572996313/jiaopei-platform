package com.cornerstone.module.organization.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
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
