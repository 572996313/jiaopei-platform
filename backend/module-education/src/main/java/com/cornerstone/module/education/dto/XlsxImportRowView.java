package com.cornerstone.module.education.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * xlsx 批量导入的单行结论，预览与落库共用同一个结构。
 *
 * <p>三份导入（学员/班级/分班）的展示列不同，但"哪一行、过没过、错在哪、有什么提示"是同一套，
 * 所以行数据统一放在 {@code cells} 里、由前端按自己配置的列取值。给每个实体各写一份行视图，
 * 只会让三份前端表格各渲染一遍同样的红字/黄字逻辑。</p>
 *
 * @param rowNum Excel 里的实际行号（含表头，从 2 起），让操作员回到文件里能定位
 * @param cells  列 key → 展示值；字典列存的是规范值，由前端按字典类型翻译成中文
 */
@Schema(description = "xlsx 批量导入单行结论")
public record XlsxImportRowView(
    @Schema(description = "Excel 行号") Integer rowNum,
    @Schema(description = "校验是否通过") boolean valid,
    @Schema(description = "错误原因,多条用分号连接;通过为 null") String error,
    @Schema(description = "提示(如已存在将跳过);无提示为 null") String notice,
    @Schema(description = "逐列展示值,key 与前端列配置一致") Map<String, String> cells
) {
}
