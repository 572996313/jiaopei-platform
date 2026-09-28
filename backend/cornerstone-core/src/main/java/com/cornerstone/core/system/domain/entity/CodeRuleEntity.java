package com.cornerstone.core.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.cornerstone.core.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 实体编号生成规则：一个资源一条，决定这一类编号长什么样、以及留空时要不要替他补上。
 *
 * <p>行里存的是<b>生效格式</b>，不是"与代码默认值的差异"：分隔符与日期段留空本身就是合法取值
 * （直接相连 / 不带日期段），不能再兼任"回落默认"。代码登记的默认值只用于维护页表单首次预填。</p>
 *
 * <p>没有新增/删除入口：资源清单在代码里，运维只能改格式与开关，不需要的资源改成停用。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_code_rule")
@Schema(description = "实体编号生成规则")
public class CodeRuleEntity extends BaseEntity {

    @Schema(description = "编码资源码,与代码登记的资源清单一一对应", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resourceCode;

    @Schema(description = "是否启用自动编号(false=留空仍必填,true=可取号且留空即补号)")
    private Boolean enabled;

    @Schema(description = "编号前缀,如 STU", requiredMode = Schema.RequiredMode.REQUIRED)
    private String prefix;

    @Schema(description = "段间分隔符,空串表示各段直接相连", allowableValues = {"-", "_", "."})
    // 字段名与列名都带 Char 后缀，不是 separator：**SEPARATOR 是 MySQL 保留字**。
    // 列名叫 separator 会让 MyBatis-Plus 生成的 SQL 直接 1064（它给 wrapper 的列名不加反引号）；
    // 字段名叫 separator 也一样炸，因为列名与字段名不同名时 MP 会拼成 `separator_char AS separator`，
    // 保留字当别名同样不被接受。接口出参不受影响（CodeRuleView/CodeRuleUpdateRequest 仍用 separator）。
    private String separatorChar;

    @Schema(description = "日期段格式,空串表示不带日期段(序号全局累加);可选 yyyyMMdd/yyMMdd/yyyyMM/yyMM/yyyy/yy")
    private String datePattern;

    @Schema(description = "序号位数,不足补零;实际号超出位数时自动加宽,不截断", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer serialLength;

    @Schema(description = "备注")
    private String remark;
}
