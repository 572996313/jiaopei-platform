package com.cornerstone.core.file.service.support;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.system.service.SystemConfigService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传文件校验：文件名、扩展名白名单、大小上限、媒体类型推导。
 *
 * <p>大小上限与扩展名白名单是运维可调参数，走 {@code sys_system_config}；
 * {@code spring.servlet.multipart.max-file-size} 只是容器硬顶，比这里宽。</p>
 */
@Component
public class FileValidator {

    public static final String MAX_SIZE_MB_KEY = "file.upload.max_size_mb";

    public static final String ALLOWED_EXTENSIONS_KEY = "file.upload.allowed_extensions";

    private static final int DEFAULT_MAX_SIZE_MB = 20;

    private static final String DEFAULT_ALLOWED_EXTENSIONS =
        "jpg,jpeg,png,webp,gif,bmp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,csv,zip,rar";

    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final SystemConfigService systemConfigService;

    public FileValidator(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要上传的文件");
        }

        String originalName = sanitizeFileName(file.getOriginalFilename());
        String extension = resolveExtension(originalName);
        assertAllowedExtension(extension);
        assertSize(file.getSize());

        return new ValidatedFile(
            originalName,
            extension,
            FileMediaTypes.resolve(extension),
            file.getSize());
    }

    /** 去掉客户端可能带来的路径前缀与控制字符，只保留文件名本身。 */
    private String sanitizeFileName(String rawName) {
        String name = rawName == null ? "" : rawName;
        name = name.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            name = "未命名文件";
        }
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "文件名过长，最多 " + MAX_FILE_NAME_LENGTH + " 个字符");
        }
        return name;
    }

    private String resolveExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return null;
        }
        String candidate = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        // 只接受字母数字扩展名：带空格、斜杠、点号的一律视为无扩展名，避免拼进对象键
        return candidate.matches("[a-z0-9]{1,10}") ? candidate : null;
    }

    private void assertAllowedExtension(String extension) {
        String configured = systemConfigService.getConfigValue(
            ALLOWED_EXTENSIONS_KEY, DEFAULT_ALLOWED_EXTENSIONS);
        List<String> allowed = Arrays.stream(configured == null ? new String[0] : configured.split(","))
            .map(item -> item.trim().toLowerCase(Locale.ROOT))
            .filter(item -> !item.isEmpty())
            .toList();
        if (allowed.isEmpty()) {
            return;
        }
        if (extension == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "无法识别文件类型，可选类型：" + String.join("、", allowed));
        }
        if (!allowed.contains(extension)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "不支持的文件类型：" + extension + "，可选类型：" + String.join("、", allowed));
        }
    }

    private void assertSize(long sizeBytes) {
        int maxMb = systemConfigService.getIntConfig(MAX_SIZE_MB_KEY, DEFAULT_MAX_SIZE_MB);
        if (maxMb > 0 && sizeBytes > maxMb * 1024L * 1024L) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件大小超过上限（" + maxMb + "MB）");
        }
    }

    /** 校验通过后的规范化信息。 */
    public record ValidatedFile(String originalName, String extension, String contentType, long sizeBytes) {
    }
}
