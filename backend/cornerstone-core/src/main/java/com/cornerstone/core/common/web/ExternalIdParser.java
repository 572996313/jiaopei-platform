package com.cornerstone.core.common.web;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把客户端传来的业务 id（纯数字或"编码+尾部数字"）解析成 Long，并给报错文案配中文名。
 *
 * <p>词表只留底座自己的字段。业务模块的字段名（校区/班级/学员…）由各自 jar 通过
 * {@link ExternalIdFieldLabelContributor} 登记，启动期由 {@link ExternalIdLabelRegistry} 合并进来
 * ——底座带行业词汇等于让每个下游项目在自己用户面前念出别人的业务名词。</p>
 */
public final class ExternalIdParser {

    /** 底座词表：机构（=租户）、角色、菜单，都是跨行业通用概念。 */
    private static final Map<String, String> CORE_LABELS = Map.of(
        "institutionid", "机构ID",
        "roleid", "角色ID",
        "menuid", "菜单ID"
    );

    /** 运行期生效的词表；未装配任何模块时等于 {@link #CORE_LABELS}。 */
    private static volatile Map<String, String> labels = CORE_LABELS;

    private ExternalIdParser() {
    }

    /** 底座词表，供守护测试断言"底座不认识业务字段"。 */
    public static Map<String, String> coreLabels() {
        return CORE_LABELS;
    }

    /** 由 {@link ExternalIdLabelRegistry} 在启动期调用；业务词表不覆盖底座同名 key。 */
    static void useLabels(Map<String, String> merged) {
        Map<String, String> effective = new LinkedHashMap<>(CORE_LABELS);
        merged.forEach(effective::putIfAbsent);
        labels = Map.copyOf(effective);
    }

    public static Long parseOptional(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequired(value, fieldName);
    }

    public static Long parseRequired(String value, String fieldName) {
        String display = displayFieldName(fieldName);
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, display + "不能为空");
        }

        String numeric = normalized.matches("\\d+")
            ? normalized
            : normalized.replaceFirst("^.*?(\\d+)$", "$1");
        if (!numeric.matches("\\d+")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, display + "必须是数字ID或以数字结尾的业务编码");
        }

        try {
            return Long.valueOf(numeric);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, display + "超出有效范围");
        }
    }

    /** 归一化字段名：转小写并去掉下划线，使 {@code campusId} 与 {@code campus_id} 归成同一条。 */
    public static String normalize(String fieldName) {
        return fieldName.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "");
    }

    private static String displayFieldName(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return "字段";
        }
        return labels.getOrDefault(normalize(fieldName), fieldName);
    }
}
