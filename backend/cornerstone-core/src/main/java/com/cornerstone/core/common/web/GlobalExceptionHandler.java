package com.cornerstone.core.common.web;

import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.failure(exception.getResultCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("; "));
        return ApiResponse.failure(ResultCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("; "));
        return ApiResponse.failure(ResultCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
            .map(violation -> violation.getMessage())
            .collect(Collectors.joining("; "));
        return ApiResponse.failure(ResultCode.VALIDATION_ERROR, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException exception) {
        return ApiResponse.failure(ResultCode.BAD_REQUEST, "请求体缺失或格式不正确");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingParameter(MissingServletRequestParameterException exception) {
        // 不接这一条就会掉进兜底分支返回 500，操作员看到的是"服务器内部错误"，而真实原因只是少带了参数
        return ApiResponse.failure(ResultCode.BAD_REQUEST, "缺少必填参数：" + exception.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return ApiResponse.failure(ResultCode.BAD_REQUEST,
            "参数 " + exception.getName() + " 取值格式不正确：" + exception.getValue());
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleAuthentication(AuthenticationException exception) {
        // 登录/改密入口抛出的认证异常消息面向用户(如"用户名或密码错误"、"账号已被禁用")，
        // 必须透传，否则会被笼统的"未登录或登录已失效"掩盖；
        // 令牌失效场景走安全过滤器的入口点，不经过这里
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = ResultCode.UNAUTHORIZED.getDefaultMessage();
        }
        return ApiResponse.failure(ResultCode.UNAUTHORIZED, message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException exception) {
        return ApiResponse.failure(ResultCode.FORBIDDEN, ResultCode.FORBIDDEN.getDefaultMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException exception) {
        return ApiResponse.failure(ResultCode.NOT_FOUND, "请求的接口不存在");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception exception) {
        // 兜底异常必须记录完整堆栈,否则线上只剩一句"服务器内部错误"无从排查
        log.error("未捕获的服务器异常", exception);
        return ApiResponse.failure(ResultCode.INTERNAL_ERROR, ResultCode.INTERNAL_ERROR.getDefaultMessage());
    }

    private String formatFieldError(FieldError error) {
        // 查询对象里的字段类型不符（如 pageSize=abc）走的是绑定异常，Spring 给的是英文技术消息，翻成中文口径
        if (error.isBindingFailure()) {
            return "参数 " + error.getField() + " 取值格式不正确：" + error.getRejectedValue();
        }
        // 校验注解统一携带中文 message（如"班级ID不能为空"），直接透传，
        // 不再拼接英文属性名前缀，避免响应里出现中英混排
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? "参数校验未通过" : message;
    }
}
