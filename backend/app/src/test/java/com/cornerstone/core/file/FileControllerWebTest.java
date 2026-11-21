package com.cornerstone.core.file;

import com.cornerstone.core.auth.aspect.PermissionGuardAspect;
import com.cornerstone.core.auth.security.RestAccessDeniedHandler;
import com.cornerstone.core.auth.security.RestAuthenticationEntryPoint;
import com.cornerstone.core.auth.security.StubAuthenticationFilter;
import com.cornerstone.core.auth.service.PermissionService;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.web.ApiResponseAdvice;
import com.cornerstone.core.common.web.GlobalExceptionHandler;
import com.cornerstone.core.common.web.TraceIdFilter;
import com.cornerstone.core.config.SecurityConfig;
import com.cornerstone.core.file.controller.FileController;
import com.cornerstone.core.file.dto.FileContent;
import com.cornerstone.core.file.dto.FileListView;
import com.cornerstone.core.file.dto.FileView;
import com.cornerstone.core.file.service.FileService;
import com.cornerstone.support.WebMvcSecurityTestConfig;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import({
    AopAutoConfiguration.class,
    SecurityConfig.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class,
    StubAuthenticationFilter.class,
    TraceIdFilter.class,
    ApiResponseAdvice.class,
    GlobalExceptionHandler.class,
    PermissionGuardAspect.class,
    WebMvcSecurityTestConfig.class
})
class FileControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/system/files")
                .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1})))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadRequiresUploadPermission() throws Exception {
        given(permissionService.hasPermission(any(), eq("system:file:upload"))).willReturn(false);

        mockMvc.perform(multipart("/api/system/files")
                .file(new MockMultipartFile("file", "a.png", "image/png", new byte[]{1}))
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void uploadReturnsFileId() throws Exception {
        given(permissionService.hasPermission(any(), eq("system:file:upload"))).willReturn(true);
        given(fileService.upload(any(), eq("student_photo"), eq(555L)))
            .willReturn(new FileView(9001L, "照片.png", "png", "image/png", 128L,
                "student_photo", 555L, LocalDateTime.now()));

        mockMvc.perform(multipart("/api/system/files")
                .file(new MockMultipartFile("file", "照片.png", "image/png", new byte[]{1, 2, 3}))
                .param("bizType", "student_photo")
                .param("bizId", "555")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fileId").value(9001))
            .andExpect(jsonPath("$.data.fileName").value("照片.png"));
    }

    @Test
    void contentStreamsBytesInlineForSafeType() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        given(fileService.openContent(9L)).willReturn(
            new FileContent(new ByteArrayInputStream(bytes), "说明.txt", "text/plain", bytes.length));

        mockMvc.perform(get("/api/system/files/9/content")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type",
                org.hamcrest.Matchers.startsWith("text/plain")))
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.startsWith("inline;")))
            .andExpect(content().bytes(bytes));
    }

    @Test
    void contentForcesAttachmentForUnsafeType() throws Exception {
        byte[] bytes = new byte[]{1, 2};
        given(fileService.openContent(9L)).willReturn(
            new FileContent(new ByteArrayInputStream(bytes), "安装包.bin",
                "application/octet-stream", bytes.length));

        mockMvc.perform(get("/api/system/files/9/content")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.startsWith("attachment;")));
    }

    @Test
    void downloadFlagForcesAttachmentEvenForImages() throws Exception {
        byte[] bytes = new byte[]{1};
        given(fileService.openContent(9L)).willReturn(
            new FileContent(new ByteArrayInputStream(bytes), "a.png", "image/png", bytes.length));

        mockMvc.perform(get("/api/system/files/9/content")
                .param("download", "true")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.startsWith("attachment;")));
    }

    @Test
    void pageRequiresManagePermission() throws Exception {
        given(permissionService.hasPermission(any(), eq("system:file:manage"))).willReturn(false);

        mockMvc.perform(get("/api/system/files/page")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void pageReturnsRecords() throws Exception {
        given(permissionService.hasPermission(any(), eq("system:file:manage"))).willReturn(true);
        given(fileService.pageFiles(any())).willReturn(new PageResult<>(
            List.of(new FileListView(9L, "a.pdf", "pdf", "application/pdf", 10L,
                null, null, 1L, 1L, LocalDateTime.now())), 1, 1, 10));

        mockMvc.perform(get("/api/system/files/page")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].fileName").value("a.pdf"));
    }

    @Test
    void deleteRequiresManagePermission() throws Exception {
        given(permissionService.hasPermission(any(), eq("system:file:manage"))).willReturn(false);

        mockMvc.perform(delete("/api/system/files/9")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void deleteReturnsMessage() throws Exception {
        // 本方法返回的已经是 ApiResponse，守住"统一响应 advice 不会二次包装"这条
        given(permissionService.hasPermission(any(), eq("system:file:manage"))).willReturn(true);

        mockMvc.perform(delete("/api/system/files/9")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("文件已删除"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void cleanupReturnsRemovedCount() throws Exception {
        given(permissionService.hasPermission(any(), eq("system:file:manage"))).willReturn(true);
        given(fileService.cleanupOrphans()).willReturn(3);

        mockMvc.perform(post("/api/system/files/cleanup-orphans")
                .header("Authorization", "Bearer stub-access-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(3));
    }
}
