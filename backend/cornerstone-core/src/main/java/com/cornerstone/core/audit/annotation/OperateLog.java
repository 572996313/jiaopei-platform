package com.cornerstone.core.audit.annotation;

import com.cornerstone.core.audit.domain.ActionType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作留痕注解(可选)。通常在 Controller 方法上标注,用于:
 * <ul>
 *   <li>覆盖默认的 {@link ActionType}(默认按 HTTP 方法推断);</li>
 *   <li>为无 {@code @Operation} 的接口提供业务操作名。</li>
 * </ul>
 *
 * <p>不标此注解的写接口也会被 {@link com.cornerstone.core.audit.aspect.OperateLogAspect}
 * 自动留痕,操作名自动取 {@code @Operation(summary)}。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperateLog {

    /** 操作名,优先于 @Operation.summary。 */
    String value() default "";

    /** 操作类型,默认按 HTTP 方法推断。 */
    ActionType actionType() default ActionType.OTHER;
}