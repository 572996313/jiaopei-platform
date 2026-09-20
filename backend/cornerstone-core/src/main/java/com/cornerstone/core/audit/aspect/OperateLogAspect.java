package com.cornerstone.core.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cornerstone.core.audit.annotation.IgnoreOperateLog;
import com.cornerstone.core.audit.annotation.OperateLog;
import com.cornerstone.core.audit.domain.ActionType;
import com.cornerstone.core.audit.domain.OperLogEntity;
import com.cornerstone.core.audit.service.OperationLogRecorder;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.dto.LoginResponse;
import com.cornerstone.core.common.security.JsonSensitiveProcessor;
import com.cornerstone.core.common.security.SensitiveStrategy;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

/**
 * 操作留痕核心切面:自动拦截所有 {@code @RestController} 写接口(HTTP 方法
 * 为 POST/PUT/DELETE/PATCH),以及登录/登出接口,异步写入日志库。
 *
 * <p>设计要点:</p>
 * <ul>
 *   <li>操作名优先取 {@code @Operation.summary}(存量接口已统一标注),无则取
 *       {@code @OperateLog.value},再降级为 "HTTP方法 + 路径";</li>
 *   <li>操作人来自 {@link AuthContext}(匿名操作为 null/未知,登录/登出从返回值提取);</li>
 *   <li>请求参数序列化为 JSON 后经 {@link JsonSensitiveProcessor} 脱敏(手机号/
 *       身份证/银行卡/密码等打码)再落库,避免敏感明文;</li>
 *   <li>写入经 {@link OperationLogRecorder} 异步执行,日志库故障不影响业务;</li>
 *   <li>{@code @IgnoreOperateLog} 可跳过(如健康检查、轮询)。</li>
 * </ul>
 */
@Aspect
@Component
public class OperateLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperateLogAspect.class);

    /** 请求参数 JSON 落库长度上限,避免超长文本撑爆日志表。 */
    private static final int MAX_PARAMS_LENGTH = 4000;

    private final ObjectMapper objectMapper;
    private final JsonSensitiveProcessor jsonSensitiveProcessor;
    private final OperationLogRecorder operationLogRecorder;

    public OperateLogAspect(ObjectMapper objectMapper,
                            JsonSensitiveProcessor jsonSensitiveProcessor,
                            OperationLogRecorder operationLogRecorder) {
        this.objectMapper = objectMapper;
        this.jsonSensitiveProcessor = jsonSensitiveProcessor;
        this.operationLogRecorder = operationLogRecorder;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController)"
        + " && !@annotation(com.cornerstone.core.audit.annotation.IgnoreOperateLog)"
        + " && !@within(com.cornerstone.core.audit.annotation.IgnoreOperateLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return joinPoint.proceed();
        }

        String httpMethod = request.getMethod();
        String path = request.getRequestURI();
        // 仅记录写操作 + 登录/登出;GET 查询不入库
        ActionType actionType = resolveActionType(httpMethod, path, joinPoint);
        if (actionType == null) {
            return joinPoint.proceed();
        }

        long start = System.nanoTime();
        Throwable failure = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            record(request, joinPoint, actionType, result, failure, durationMs);
        }
    }

    private void record(HttpServletRequest request, ProceedingJoinPoint joinPoint, ActionType actionType,
                        Object result, Throwable failure, long durationMs) {
        try {
            OperLogEntity entity = new OperLogEntity();
            entity.setTraceId(traceId());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setMethod(request.getMethod());
            entity.setPath(truncate(request.getRequestURI(), 255));
            entity.setClientIp(resolveClientIp(request));
            entity.setUserAgent(truncate(request.getHeader("User-Agent"), 512));
            entity.setOperation(resolveOperationName(joinPoint, request));
            entity.setActionType(actionType.name());
            entity.setSuccess(failure == null);
            entity.setErrorMsg(failure == null ? null : truncate(failure.getMessage(), 1024));
            entity.setDurationMs(durationMs);

            resolveOperator(entity, actionType, result);
            if (failure == null) {
                entity.setParams(buildParams(joinPoint));
            }
            operationLogRecorder.record(entity);
        } catch (Exception e) {
            // 切面自身兜底:留痕失败绝不回抛给业务
            log.warn("操作留痕记录失败(path={}):{}", request.getRequestURI(), e.getMessage());
        }
    }

    /** 操作名:OperateLog.value → @Operation.summary → "HTTP方法 + 路径"。 */
    private String resolveOperationName(ProceedingJoinPoint joinPoint, HttpServletRequest request) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        OperateLog operateLog = signature.getMethod().getAnnotation(OperateLog.class);
        if (operateLog != null && !operateLog.value().isBlank()) {
            return truncate(operateLog.value(), 128);
        }
        Operation operation = signature.getMethod().getAnnotation(Operation.class);
        String summary = operation == null ? null : operation.summary();
        if (summary != null && !summary.isBlank()) {
            return truncate(summary, 128);
        }
        return request.getMethod() + " " + request.getRequestURI();
    }

    /** 操作类型:登录/登出接口固定分类,其余按 HTTP 方法推断;GET/OPTIONS 等返回 null 表示不记录。 */
    private ActionType resolveActionType(String httpMethod, String path, ProceedingJoinPoint joinPoint) {
        if ("/api/auth/login".equals(path) || path.endsWith("/api/auth/wechat/login")) {
            return ActionType.LOGIN;
        }
        if ("/api/auth/logout".equals(path)) {
            return ActionType.LOGOUT;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        OperateLog operateLog = signature.getMethod().getAnnotation(OperateLog.class);
        if (operateLog != null && operateLog.actionType() != ActionType.OTHER) {
            return operateLog.actionType();
        }
        ActionType inferred = ActionType.fromMethod(httpMethod);
        return inferred == ActionType.OTHER ? null : inferred;
    }

    /** 操作人:优先登录用户;登录/登出从返回体(可能是 ApiResponse 包装)提取 LoginResponse。 */
    private void resolveOperator(OperLogEntity entity, ActionType actionType, Object result) {
        LoginResponse login = extractLogin(result);
        if (actionType == ActionType.LOGIN && login != null) {
            entity.setUserId(login.userId());
            entity.setUsername(login.username());
            entity.setOrgId(login.orgId());
            entity.setPortal(login.portal());
            return;
        }
        Optional<AuthenticatedUser> current = AuthContext.currentUser();
        current.ifPresent(user -> {
            entity.setUserId(user.userId());
            entity.setUsername(user.username());
            entity.setOrgId(user.orgId());
            entity.setPortal(user.portal());
        });
        if (entity.getUsername() == null) {
            // 匿名操作(如小程序自助注册)统一占位
            entity.setUsername("system/anonymous");
        }
    }

    /** 从返回值提取 LoginResponse:兼容直接返回与 ApiResponse 包装两种形态。 */
    private LoginResponse extractLogin(Object result) {
        if (result instanceof LoginResponse direct) {
            return direct;
        }
        if (result instanceof com.cornerstone.core.common.api.ApiResponse<?> api) {
            Object data = api.getData();
            if (data instanceof LoginResponse wrapped) {
                return wrapped;
            }
        }
        return null;
    }

    /** 请求参数序列化 + 脱敏。过滤 servlet 容器 / 文件流等不可序列化参数。 */
    private String buildParams(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            List<Object> recordable = new ArrayList<>(args.length);
            for (Object arg : args) {
                if (arg == null
                    || arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse
                    || arg instanceof MultipartFile
                    || isFrameworkParam(arg)) {
                    continue;
                }
                recordable.add(arg);
            }
            if (recordable.isEmpty()) {
                return null;
            }
            // 先序列化为 JSON 树,再脱敏:JsonSensitiveProcessor 基于 Map/List 结构识别,
            // 不能直接处理 record 对象
            Object tree = objectMapper.readValue(objectMapper.writeValueAsString(recordable), Object.class);
            Object masked = jsonSensitiveProcessor.process(tree, SensitiveStrategy.MASK);
            String json = objectMapper.writeValueAsString(masked);
            return truncate(json, MAX_PARAMS_LENGTH);
        } catch (Exception e) {
            return "{\"_error\":\"参数序列化失败\"}";
        }
    }

    private boolean isFrameworkParam(Object arg) {
        return arg instanceof org.springframework.validation.BindingResult
            || arg instanceof org.springframework.web.bind.support.WebRequestDataBinder
            || arg.getClass().getName().startsWith("org.springframework.web");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first, 64);
            }
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private String traceId() {
        return MDC.get("traceId");
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}