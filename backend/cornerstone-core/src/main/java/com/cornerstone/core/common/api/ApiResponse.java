package com.cornerstone.core.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Getter
public class ApiResponse<T> {

    private String code;
    private boolean success;
    private String message;
    private T data;
    private OffsetDateTime timestamp;
    private String traceId;
    private String path;

    private ApiResponse() {
    }

    private ApiResponse(String code, boolean success, String message, T data, OffsetDateTime timestamp, String traceId,
                        String path) {
        this.code = code;
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.path = path;
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("OK", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
            ResultCode.SUCCESS.getCode(),
            true,
            message,
            data,
            OffsetDateTime.now(),
            currentTraceId(),
            currentPath()
        );
    }

    public static <T> ApiResponse<T> failure(ResultCode code, String message) {
        return new ApiResponse<>(
            code.getCode(),
            false,
            message,
            null,
            OffsetDateTime.now(),
            currentTraceId(),
            currentPath()
        );
    }

    private static String currentTraceId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object traceId = attributes.getAttribute("traceId", RequestAttributes.SCOPE_REQUEST);
        return traceId instanceof String value ? value : null;
    }

    private static String currentPath() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            return request.getRequestURI();
        }
        return null;
    }
}
