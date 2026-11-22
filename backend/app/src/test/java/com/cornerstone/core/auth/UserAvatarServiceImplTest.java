package com.cornerstone.core.auth;

import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.auth.service.impl.UserAvatarPersistence;
import com.cornerstone.core.auth.service.impl.UserAvatarServiceImpl;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.file.dto.FileView;
import com.cornerstone.core.file.service.FileService;
import com.cornerstone.core.system.service.SystemConfigService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAvatarServiceImplTest {

    private final FileService fileService = mock(FileService.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final UserAvatarPersistence userAvatarPersistence = mock(UserAvatarPersistence.class);
    private final UserAvatarServiceImpl service = new UserAvatarServiceImpl(
        fileService, systemConfigService, userAvatarPersistence);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsEmptyFile() {
        // updateAvatar 先要登录态再校验文件，匿名请求在 requireUser 就被拦下
        authenticate();
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.updateAvatar(new MockMultipartFile("file", "avatar.png", "image/png", new byte[0])));
        assertTrue(exception.getMessage().contains("请选择要上传的头像图片"));
    }

    @Test
    void rejectsNonImageExtension() {
        authenticate();
        byte[] bytes = pngBytes();
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.updateAvatar(new MockMultipartFile("file", "avatar.gif", "image/gif", bytes)));
        assertTrue(exception.getMessage().contains("JPG、PNG"));
    }

    @Test
    void rejectsOversizeFile() {
        authenticate();
        when(systemConfigService.getIntConfig(UserAvatarServiceImpl.MAX_SIZE_MB_KEY, 2)).thenReturn(2);
        byte[] bytes = new byte[3 * 1024 * 1024];
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.updateAvatar(new MockMultipartFile("file", "avatar.png", "image/png", bytes)));
        assertTrue(exception.getMessage().contains("2 MB"));
    }

    @Test
    void rejectsContentThatIsNotReallyAnImage() {
        authenticate();
        when(systemConfigService.getIntConfig(UserAvatarServiceImpl.MAX_SIZE_MB_KEY, 2)).thenReturn(2);
        byte[] bytes = "<html>not an image</html>".getBytes();
        BusinessException exception = assertThrows(BusinessException.class,
            () -> service.updateAvatar(new MockMultipartFile("file", "avatar.png", "image/png", bytes)));
        assertTrue(exception.getMessage().contains("不是有效的图片"));
    }

    @Test
    void uploadsThenReplacesAvatarInOneStep() throws Exception {
        authenticate();
        when(systemConfigService.getIntConfig(UserAvatarServiceImpl.MAX_SIZE_MB_KEY, 2)).thenReturn(2);
        byte[] bytes = pngBytes();
        FileView uploaded = new FileView(200L, "avatar.png", "png", "image/png",
            (long) bytes.length, null, null, LocalDateTime.now());
        when(fileService.upload(any(), isNull(), isNull())).thenReturn(uploaded);

        Long fileId = service.updateAvatar(
            new MockMultipartFile("file", "avatar.png", "image/png", bytes));

        assertEquals(200L, fileId.longValue());
        // 上传保持未绑定，绑定与回写用户行交给同一个事务
        verify(fileService).upload(any(), isNull(), isNull());
        verify(userAvatarPersistence).replace(1L, 200L);
    }

    @Test
    void clearAvatarDelegatesToCurrentUser() {
        authenticate();
        service.clearAvatar();
        verify(userAvatarPersistence).clear(1L);
    }

    @Test
    void releaseForUserIgnoresNullAndDelegatesOtherwise() {
        service.releaseForUser(null);
        verify(userAvatarPersistence, never()).release(any());

        service.releaseForUser(7L);
        verify(userAvatarPersistence).release(7L);
    }

    private void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(
            1L, 1L, "admin", Set.of("PLATFORM_ADMIN"), Set.of(), false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }

    private static byte[] pngBytes() {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
