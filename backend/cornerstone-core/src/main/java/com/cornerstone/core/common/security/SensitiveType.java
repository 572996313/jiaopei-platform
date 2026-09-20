package com.cornerstone.core.common.security;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 敏感数据类型枚举。除 OTHER 外均提供默认掩码规则。
 *
 * <p>识别优先级:字段名关键词(强,先于值格式) → 值格式兜底(弱,仅在字段名
 * 无法定位时启用,避免误伤课程名、机构名等普通文本)。</p>
 */
@Schema(description = "敏感数据类型")
public enum SensitiveType {

    /** 手机号,如 138****1234 */
    PHONE,

    /** 二代身份证号,如 110***********1234 */
    ID_CARD,

    /** 银行卡号,如 6222 **** **** 1234 */
    BANK_CARD,

    /** 密码/口令,全量打码 */
    PASSWORD,

    /** 邮箱,如 a***@example.com */
    EMAIL,

    /** 人名,如 张* */
    NAME,

    /** 地址,保留前 6 个字符 */
    ADDRESS,

    /** 其他(仅按值格式兜底识别) */
    OTHER
}