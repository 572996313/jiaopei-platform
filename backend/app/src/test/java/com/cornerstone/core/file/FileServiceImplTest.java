package com.cornerstone.core.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.config.properties.CornerstoneProperties;
import com.cornerstone.core.file.domain.entity.FileEntity;
import com.cornerstone.core.file.dto.FileView;
import com.cornerstone.core.file.mapper.FileMapper;
import com.cornerstone.core.file.service.impl.FileServiceImpl;
import com.cornerstone.core.file.service.support.FileValidator;
import com.cornerstone.core.file.storage.FileStorage;
import com.cornerstone.core.system.service.SystemConfigService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceImplTest {

    private FileMapper fileMapper;

    private FileStorage fileStorage;

    private FileValidator fileValidator;

    private SystemConfigService systemConfigService;

    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileMapper = mock(FileMapper.class);
        fileStorage = mock(FileStorage.class);
        fileValidator = mock(FileValidator.class);
        systemConfigService = mock(SystemConfigService.class);
        CornerstoneProperties properties = new CornerstoneProperties();
        properties.getFile().setBucket("test-bucket");
        fileService = new FileServiceImpl(fileMapper, fileStorage, fileValidator, systemConfigService, properties);
        login(100L, Set.of("INSTITUTION_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadStoresObjectAndPersistsMetadataWithInstitutionFromLogin() {
        when(fileValidator.validate(any())).thenReturn(
            new FileValidator.ValidatedFile("照片.png", "png", "image/png", 128L));
        doAnswer(invocation -> {
            FileEntity entity = invocation.getArgument(0);
            entity.setId(9001L);
            return 1;
        }).when(fileMapper).insert(any(FileEntity.class));

        MockMultipartFile file = new MockMultipartFile(
            "file", "照片.png", "image/png", new byte[128]);
        FileView view = fileService.upload(file, null, null);

        assertThat(view.fileId()).isEqualTo(9001L);
        assertThat(view.fileName()).isEqualTo("照片.png");
        assertThat(view.contentType()).isEqualTo("image/png");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).put(keyCaptor.capture(), any(), eq(128L), eq("image/png"));
        assertThat(keyCaptor.getValue()).startsWith("100/").endsWith(".png");

        ArgumentCaptor<FileEntity> entityCaptor = ArgumentCaptor.forClass(FileEntity.class);
        verify(fileMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getInstitutionId()).isEqualTo(100L);
        assertThat(entityCaptor.getValue().getBucket()).isEqualTo("test-bucket");
        assertThat(entityCaptor.getValue().getUploadedBy()).isEqualTo(1L);
    }

    @Test
    void uploadRollsBackObjectWhenMetadataInsertFails() {
        when(fileValidator.validate(any())).thenReturn(
            new FileValidator.ValidatedFile("a.pdf", "pdf", "application/pdf", 8L));
        when(fileMapper.insert(any(FileEntity.class))).thenThrow(new IllegalStateException("db down"));

        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[8]);
        assertThatThrownBy(() -> fileService.upload(file, null, null))
            .isInstanceOf(IllegalStateException.class);

        verify(fileStorage).delete(anyString());
    }

    @Test
    void readingAnotherInstitutionsFileIsForbidden() {
        FileEntity entity = fileEntity(7L, 999L, null);
        when(fileMapper.selectById(7L)).thenReturn(entity);

        assertThatThrownBy(() -> fileService.getFile(7L))
            .isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getResultCode()).isEqualTo(ResultCode.FORBIDDEN));
    }

    @Test
    void platformAdminCanReadAnyInstitution() {
        login(1L, Set.of("PLATFORM_ADMIN"));
        when(fileMapper.selectById(7L)).thenReturn(fileEntity(7L, 999L, null));

        FileView view = fileService.getFile(7L);

        assertThat(view.fileId()).isEqualTo(7L);
    }

    @Test
    void deletingFileBoundToBusinessIsRejected() {
        when(fileMapper.selectById(7L)).thenReturn(fileEntity(7L, 100L, 555L));

        assertThatThrownBy(() -> fileService.deleteFile(7L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已被业务数据引用");

        verify(fileStorage, never()).delete(anyString());
        verify(fileMapper, never()).physicalDeleteById(any());
    }

    @Test
    void bindRejectsFileAlreadyBoundToAnotherBusinessRecord() {
        FileEntity existing = fileEntity(7L, 100L, 555L);
        when(fileMapper.selectById(7L)).thenReturn(existing);

        assertThatThrownBy(() -> fileService.bind(List.of(7L), "student_photo", 666L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("已被其他业务数据引用");

        verify(fileMapper, never()).update(any(), any());
    }

    @Test
    void cleanupRemovesOnlyExpiredUnboundFilesAndSkipsFailedObjects() {
        when(systemConfigService.getIntConfig(FileServiceImpl.ORPHAN_RETENTION_HOURS_KEY, 24)).thenReturn(24);
        FileEntity removable = fileEntity(1L, 100L, null);
        FileEntity failing = fileEntity(2L, 100L, null);
        when(fileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(removable, failing));
        doThrow(new BusinessException(ResultCode.INTERNAL_ERROR, "存储不可用"))
            .when(fileStorage).delete("institution/2026/11/two.pdf");

        int removed = fileService.cleanupOrphans();

        assertThat(removed).isEqualTo(1);
        verify(fileMapper).physicalDeleteById(1L);
        verify(fileMapper, never()).physicalDeleteById(2L);
    }

    @Test
    void cleanupStopsWhenRetentionConfiguredToZero() {
        when(systemConfigService.getIntConfig(FileServiceImpl.ORPHAN_RETENTION_HOURS_KEY, 24)).thenReturn(0);

        assertThat(fileService.cleanupOrphans()).isZero();
        verify(fileMapper, never()).selectList(any());
    }

    private void login(Long orgId, Set<String> roles) {
        AuthenticatedUser user = new AuthenticatedUser(
            1L, orgId, "admin", roles, Set.of(), false, "admin");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "token", Set.of()));
    }

    private FileEntity fileEntity(Long id, Long institutionId, Long bizId) {
        FileEntity entity = new FileEntity();
        entity.setId(id);
        entity.setInstitutionId(institutionId);
        entity.setBucket("test-bucket");
        entity.setObjectKey(id == 1L ? "institution/2026/11/one.pdf" : "institution/2026/11/two.pdf");
        entity.setOriginalName("f" + id + ".pdf");
        entity.setContentType("application/pdf");
        entity.setSizeBytes(10L);
        entity.setBizId(bizId);
        return entity;
    }
}
