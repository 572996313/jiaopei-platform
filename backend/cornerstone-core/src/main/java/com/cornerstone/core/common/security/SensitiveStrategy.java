package com.cornerstone.core.common.security;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 敏感数据的处理策略,一套策略覆盖「不变 / 脱敏 / 加密 / 加密且脱敏」全场景。
 *
 * <ul>
 *   <li>PLAIN:保持原样(明文),适合不可脱敏的内部链路;</li>
 *   <li>MASK:仅脱敏(不可逆),操作日志落库默认策略;</li>
 *   <li>ENCRYPT:加密存储(可逆),密文经 {@link SensitiveCipher#decrypt} 还原;</li>
 *   <li>ENCRYPT_MASK:加密存储 + 展示脱敏(存储为密文,查询展示前先解密再脱敏)。</li>
 * </ul>
 */
@Schema(description = "敏感数据处理策略")
public enum SensitiveStrategy {

    /** 不变(明文) */
    PLAIN,

    /** 仅脱敏(不可逆) */
    MASK,

    /** 加密可解密 */
    ENCRYPT,

    /** 加密存储 + 展示脱敏 */
    ENCRYPT_MASK
}