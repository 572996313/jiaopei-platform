package com.cornerstone.module.organization.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户默认校区偏好。
 *
 * <p>原先挂在底座用户表的 default_campus_id 列上，会让 cornerstone-core 的建表脚本
 * 依赖业务模块的校区表，第二项目因此无法只依赖底座启动，故外移到本模块。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("jp_user_campus_pref")
@Schema(description = "用户默认校区偏好")
public class UserCampusPrefEntity extends BaseEntity {

    @Schema(description = "机构ID")
    private Long institutionId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "默认校区ID,为空表示未设置")
    private Long campusId;
}
