package com.cornerstone.core.auth.service.impl;

import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.service.UserAvatarService;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.file.dto.FileView;
import com.cornerstone.core.file.service.FileService;
import com.cornerstone.core.system.service.SystemConfigService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本人头像上传与清除。
 *
 * <p>校验独立于 {@code FileValidator}：那里的白名单是全局文件策略（含 PDF、压缩包等），
 * 头像要收窄到图片，且要真解码一次，防止"改名成 .png 的 HTML"被当成图片内联输出。</p>
 */
@Service
public class UserAvatarServiceImpl implements UserAvatarService {

    private static final Logger log = LoggerFactory.getLogger(UserAvatarServiceImpl.class);

    /** 头像大小上限（MB）的系统参数键 */
    public static final String MAX_SIZE_MB_KEY = "user.avatar.max_size_mb";

    private static final int DEFAULT_MAX_SIZE_MB = 2;

    /** JDK ImageIO 原生支持 JPEG/PNG；webp 需要额外解码器，本期不放开。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");

    private static final Map<String, String> EXTENSION_FORMATS = Map.of(
        "jpg", "JPEG",
        "jpeg", "JPEG",
        "png", "PNG");

    /** 最长边上限，挡住解压炸弹（按尺寸卡，不整图解码） */
    private static final int MAX_DIMENSION = 4096;

    private final FileService fileService;

    private final SystemConfigService systemConfigService;

    private final UserAvatarPersistence userAvatarPersistence;

    public UserAvatarServiceImpl(FileService fileService,
                                 SystemConfigService systemConfigService,
                                 UserAvatarPersistence userAvatarPersistence) {
        this.fileService = fileService;
        this.systemConfigService = systemConfigService;
        this.userAvatarPersistence = userAvatarPersistence;
    }

    @Override
    public Long updateAvatar(MultipartFile file) {
        AuthenticatedUser user = AuthContext.requireUser();
        validate(file);

        // 上传保持未绑定；绑定与回写用户行在 UserAvatarPersistence 的同一事务里完成
        FileView uploaded = fileService.upload(file, null, null);
        Long newFileId = uploaded.fileId();
        try {
            userAvatarPersistence.replace(user.userId(), newFileId);
        } catch (RuntimeException exception) {
            // 事务已回滚，新文件仍处于未绑定状态，交给孤儿清理回收；不主动删，避免误删已提交的引用
            log.warn("头像落库失败，新文件转为孤儿等待清理：{}", newFileId, exception);
            throw exception;
        }
        return newFileId;
    }

    @Override
    public void clearAvatar() {
        AuthenticatedUser user = AuthContext.requireUser();
        userAvatarPersistence.clear(user.userId());
    }

    @Override
    public void releaseForUser(Long userId) {
        if (userId == null) {
            return;
        }
        userAvatarPersistence.release(userId);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请选择要上传的头像图片");
        }
        String extension = resolveExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "头像只支持 JPG、PNG 格式");
        }
        assertSize(file.getSize());
        assertDecodableImage(file, extension);
    }

    private void assertSize(long sizeBytes) {
        int maxMb = systemConfigService.getIntConfig(MAX_SIZE_MB_KEY, DEFAULT_MAX_SIZE_MB);
        if (maxMb > 0 && sizeBytes > maxMb * 1024L * 1024L) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "头像大小不能超过 " + maxMb + " MB");
        }
    }

    /**
     * 真解码一次：没有可用 ImageReader = 不是有效图片；格式与扩展名不符同样拒绝，
     * 避免按扩展名推导出的 Content-Type 与实际内容不一致。
     */
    private void assertDecodableImage(MultipartFile file, String extension) {
        try (InputStream input = file.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName();
                if (format == null || !EXTENSION_FORMATS.get(extension).equalsIgnoreCase(format)) {
                    throw new BusinessException(ResultCode.BAD_REQUEST, "头像文件内容与扩展名不一致，请重新选择");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw invalidImage();
                }
                if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new BusinessException(ResultCode.BAD_REQUEST,
                        "头像尺寸过大，最长边不能超过 " + MAX_DIMENSION + " 像素");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "读取头像图片失败，请重新选择");
        }
    }

    private BusinessException invalidImage() {
        return new BusinessException(ResultCode.BAD_REQUEST, "头像文件不是有效的图片");
    }

    /** 只取文件名里的扩展名，路径前缀、控制字符一律忽略。 */
    private String resolveExtension(String rawName) {
        String name = rawName == null ? "" : rawName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return null;
        }
        String candidate = name.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
        return candidate.matches("[a-z0-9]{1,10}") ? candidate : null;
    }
}
