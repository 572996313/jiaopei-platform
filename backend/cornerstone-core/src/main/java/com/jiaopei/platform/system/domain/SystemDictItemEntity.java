package com.jiaopei.platform.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiaopei.platform.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_item")
public class SystemDictItemEntity extends BaseEntity {

    private Long typeId;
    private String itemCode;
    private String itemName;
    private String itemValue;
    private Integer sortNo;
    private String status;
    private String remark;
}
