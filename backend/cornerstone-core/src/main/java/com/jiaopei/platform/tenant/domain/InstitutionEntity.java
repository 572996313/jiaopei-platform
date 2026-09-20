package com.jiaopei.platform.tenant.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_institution")
public class InstitutionEntity extends BaseEntity {

    private String orgCode;
    private String orgName;
    private String shortName;
    private String contactName;
    private String contactPhone;
    private String provinceCode;
    private String cityCode;
    private String districtCode;
    private String address;
    private String timezone;
    private String status;
    private LocalDate openedAt;
    private String remark;
}
