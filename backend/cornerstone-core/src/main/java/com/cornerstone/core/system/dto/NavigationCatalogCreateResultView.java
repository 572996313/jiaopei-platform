package com.cornerstone.core.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 新建顶层分组的返回：新分组的 id 与保存后的完整导航。
 *
 * grantedRoleIdCount 要回给前端——建分组时同批把它授给了所有在职角色，
 * 客户必须看到"授了几个角色"，否则他不知道这个新分组别人能不能看见。
 */
@Schema(description = "新建顶层分组结果")
public record NavigationCatalogCreateResultView(
    @Schema(description = "新建分组的节点ID(前端把改动挂在它下面的页面一起提交)") Long id,
    @Schema(description = "同批授权到的在职角色数") long grantedRoleIdCount,
    @Schema(description = "保存后的完整导航清单") List<NavigationView> nodes
) {
}
