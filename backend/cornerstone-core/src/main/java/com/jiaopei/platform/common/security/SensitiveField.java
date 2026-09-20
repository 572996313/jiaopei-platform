package com.jiaopei.platform.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级敏感标注,供实体 / DTO 声明敏感字段的语义与处理策略。
 *
 * <p>当前由 {@link SensitiveFieldSerializer}(Jackson)在序列化出口生效;
 * 操作日志的请求参数脱敏走 {@link JsonSensitiveProcessor}(基于 JSON 树,
 * 不依赖注解),两条链路共用 {@link DataMasking} 与 {@link SensitiveCipher}。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SensitiveField {

    /** 字段对应的敏感数据类型。 */
    SensitiveType type() default SensitiveType.OTHER;

    /** 序列化时应用的处理策略,默认脱敏。 */
    SensitiveStrategy strategy() default SensitiveStrategy.MASK;
}