package com.cornerstone.core.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记无需操作留痕的接口,如健康检查、轮询查询、登录接口本身(登录由
 * OperateLogAspect 特殊处理,也支持用此注解跳过)。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreOperateLog {
}