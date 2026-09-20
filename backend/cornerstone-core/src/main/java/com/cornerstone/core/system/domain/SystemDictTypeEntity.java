package com.cornerstone.core.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
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
