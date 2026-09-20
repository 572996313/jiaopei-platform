package com.jiaopei.platform.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SystemDictTypeEntity extends BaseEntity {

    private String typeCode;
    private String typeName;
    private String status;
    private String remark;
}
