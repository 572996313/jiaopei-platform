package com.cornerstone.core.file.service.support;

import java.util.Map;

/**
 * 按扩展名推导媒体类型。
 *
 * <p>不采信客户端上报的 {@code Content-Type}：上传方可声称图片实为 HTML，服务端一旦
 * 按声明 inline 输出就是存储型 XSS。表里没有的扩展名一律回落 octet-stream 并强制下载。</p>
 */
public final class FileMediaTypes {

    public static final String OCTET_STREAM = "application/octet-stream";

    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
        Map.entry("jpg", "image/jpeg"),
        Map.entry("jpeg", "image/jpeg"),
        Map.entry("png", "image/png"),
        Map.entry("gif", "image/gif"),
        Map.entry("webp", "image/webp"),
        Map.entry("bmp", "image/bmp"),
        Map.entry("pdf", "application/pdf"),
        Map.entry("txt", "text/plain"),
        Map.entry("csv", "text/csv"),
        Map.entry("doc", "application/msword"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xls", "application/vnd.ms-excel"),
        Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("ppt", "application/vnd.ms-powerpoint"),
        Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        Map.entry("zip", "application/zip"),
        Map.entry("rar", "application/vnd.rar")
    );

    private FileMediaTypes() {
    }

    public static String resolve(String extension) {
        if (extension == null || extension.isBlank()) {
            return OCTET_STREAM;
        }
        return BY_EXTENSION.getOrDefault(extension.toLowerCase(), OCTET_STREAM);
    }

    /** 只有图片、PDF 与纯文本允许浏览器内联打开，其余一律走附件下载。 */
    public static boolean inlineSafe(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.startsWith("image/")
            || "application/pdf".equals(contentType)
            || "text/plain".equals(contentType);
    }
}
