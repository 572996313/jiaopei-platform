package com.cornerstone.core.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@TableName("sys_system_config")
@Schema(description = "系统参数配置")
public class SystemConfigEntity extends BaseEntity {

    @Schema(description = "配置键", requiredMode = Schema.RequiredMode.REQUIRED)
    private String configKey;

    @Schema(description = "配置值", requiredMode = Schema.RequiredMode.REQUIRED)
    private String configValue;

    @Schema(description = "值类型：string/int/boolean/json", requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"string", "int", "boolean", "json"})
    private String valueType;

    @Schema(description = "配置分组：auth/system/business", requiredMode = Schema.RequiredMode.REQUIRED)
    private String configGroup;

    @Schema(description = "显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    @Schema(description = "配置说明")
    private String description;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "是否系统配置（系统配置不可删除）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean isSystem;

    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sortOrder;

    @Schema(description = "状态：active/disabled", allowableValues = {"active", "disabled"})
    private String status;

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getConfigGroup() {
        return configGroup;
    }

    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Boolean getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Boolean isSystem) {
        this.isSystem = isSystem;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
