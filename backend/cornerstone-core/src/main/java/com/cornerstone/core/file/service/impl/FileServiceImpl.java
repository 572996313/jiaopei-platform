package com.cornerstone.core.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cornerstone.core.auth.context.AuthContext;
import com.cornerstone.core.auth.context.AuthenticatedUser;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.core.common.model.PageSortSupport;
import com.cornerstone.core.config.properties.CornerstoneProperties;
import com.cornerstone.core.file.domain.entity.FileEntity;
import com.cornerstone.core.file.dto.FileContent;
import com.cornerstone.core.file.dto.FileListView;
import com.cornerstone.core.file.dto.FilePageQuery;
import com.cornerstone.core.file.dto.FileView;
import com.cornerstone.core.file.mapper.FileMapper;
import com.cornerstone.core.file.service.FileService;
import com.cornerstone.core.file.service.support.FileValidator;
import com.cornerstone.core.file.storage.FileStorage;
import com.cornerstone.core.system.service.SystemConfigService;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    /** 孤儿文件保留时长（小时），小于等于 0 表示停止自动清理 */
    public static final String ORPHAN_RETENTION_HOURS_KEY = "file.orphan.retention_hours";

    private static final int DEFAULT_ORPHAN_RETENTION_HOURS = 24;

    private static final int CLEANUP_BATCH_LIMIT = 500;

    private static final int MAX_BIZ_TYPE_LENGTH = 64;

    private static final String PLATFORM_ADMIN_ROLE = "PLATFORM_ADMIN";

    private static final Map<String, SFunction<FileEntity, ?>> FILE_SORTABLE = Map.of(
        "fileName", FileEntity::getOriginalName,
        "sizeBytes", FileEntity::getSizeBytes,
        "createdAt", FileEntity::getCreatedAt);

    private final FileMapper fileMapper;

    private final FileStorage fileStorage;

    private final FileValidator fileValidator;

    private final SystemConfigService systemConfigService;

    private final CornerstoneProperties cornerstoneProperties;

    public FileServiceImpl(FileMapper fileMapper,
                           FileStorage fileStorage,
                           FileValidator fileValidator,
                           SystemConfigService systemConfigService,
                           CornerstoneProperties cornerstoneProperties) {
        this.fileMapper = fileMapper;
        this.fileStorage = fileStorage;
        this.fileValidator = fileValidator;
        this.systemConfigService = systemConfigService;
        this.cornerstoneProperties = cornerstoneProperties;
    }

    @Override
    public FileView upload(MultipartFile file, String bizType, Long bizId) {
        AuthenticatedUser user = AuthContext.requireUser();
        Long institutionId = user.orgId();
        if (institutionId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前账号未归属机构，无法上传文件");
        }

        String normalizedBizType = normalizeBizType(bizType, bizId);
        FileValidator.ValidatedFile validated = fileValidator.validate(file);
        String objectKey = buildObjectKey(institutionId, validated.extension());
        String bucket = cornerstoneProperties.getFile().getBucket();

        try (InputStream inputStream = file.getInputStream()) {
            fileStorage.put(objectKey, inputStream, validated.sizeBytes(), validated.contentType());
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "读取上传文件失败，请重试");
        }

        FileEntity entity = new FileEntity();
        entity.setInstitutionId(institutionId);
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setOriginalName(validated.originalName());
        entity.setExtension(validated.extension());
        entity.setContentType(validated.contentType());
        entity.setSizeBytes(validated.sizeBytes());
        entity.setBizType(normalizedBizType);
        entity.setBizId(bizId);
        entity.setUploadedBy(user.userId());
        entity.setCreatedBy(user.userId());
        try {
            fileMapper.insert(entity);
        } catch (RuntimeException exception) {
            // 元数据落库失败时把已经写入的对象删掉，避免桶里留下无主对象
            deleteObjectQuietly(objectKey);
            throw exception;
        }
        return toView(entity);
    }

    @Override
    public FileView getFile(Long fileId) {
        FileEntity entity = requireFile(fileId);
        assertSameInstitution(entity);
        return toView(entity);
    }

    @Override
    public FileContent openContent(Long fileId) {
        FileEntity entity = requireFile(fileId);
        assertSameInstitution(entity);
        InputStream stream = fileStorage.get(entity.getObjectKey());
        return new FileContent(stream, entity.getOriginalName(), entity.getContentType(),
            entity.getSizeBytes() == null ? 0L : entity.getSizeBytes());
    }

    @Override
    public PageResult<FileListView> pageFiles(FilePageQuery query) {
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<>();
        AuthenticatedUser user = AuthContext.requireUser();
        if (!isPlatformAdmin(user)) {
            if (user.orgId() == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "当前账号未归属机构");
            }
            wrapper.eq(FileEntity::getInstitutionId, user.orgId());
        }

        String keyword = trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.like(FileEntity::getOriginalName, keyword);
        }
        String bizType = trimToNull(query.getBizType());
        if (bizType != null) {
            wrapper.eq(FileEntity::getBizType, bizType);
        }
        if (query.getBizId() != null) {
            wrapper.eq(FileEntity::getBizId, query.getBizId());
        }
        if (query.getCreatedFrom() != null) {
            wrapper.ge(FileEntity::getCreatedAt, query.getCreatedFrom());
        }
        if (query.getCreatedTo() != null) {
            wrapper.le(FileEntity::getCreatedAt, query.getCreatedTo());
        }
        if (!PageSortSupport.apply(wrapper, query, FILE_SORTABLE, FileEntity::getId)) {
            wrapper.orderByDesc(FileEntity::getCreatedAt);
        }

        Page<FileEntity> page = fileMapper.selectPage(
            new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toListView).toList());
    }

    @Override
    public void deleteFile(Long fileId) {
        FileEntity entity = requireFile(fileId);
        assertSameInstitution(entity);
        if (entity.getBizId() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "文件「" + entity.getOriginalName() + "」已被业务数据引用，请先在业务处解除绑定再删除");
        }
        // 先删对象再删行：对象删不掉时保留元数据，避免出现"文件行还在、内容已经没了"
        fileStorage.delete(entity.getObjectKey());
        fileMapper.physicalDeleteById(entity.getId());
    }

    @Override
    public int cleanupOrphans() {
        int retentionHours = systemConfigService.getIntConfig(
            ORPHAN_RETENTION_HOURS_KEY, DEFAULT_ORPHAN_RETENTION_HOURS);
        if (retentionHours <= 0) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusHours(retentionHours);

        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(FileEntity::getBizId)
            .lt(FileEntity::getCreatedAt, cutoff);
        // 定时任务没有登录态，清理全机构；页面手工触发时只清当前机构
        AuthContext.currentUser().ifPresent(user -> {
            if (!isPlatformAdmin(user)) {
                if (user.orgId() == null) {
                    throw new BusinessException(ResultCode.FORBIDDEN, "当前账号未归属机构");
                }
                wrapper.eq(FileEntity::getInstitutionId, user.orgId());
            }
        });
        wrapper.orderByAsc(FileEntity::getCreatedAt)
            .last("LIMIT " + CLEANUP_BATCH_LIMIT);

        List<FileEntity> orphans = fileMapper.selectList(wrapper);
        int removed = 0;
        for (FileEntity orphan : orphans) {
            try {
                fileStorage.delete(orphan.getObjectKey());
            } catch (RuntimeException exception) {
                log.warn("孤儿文件对象删除失败，跳过：{}", orphan.getObjectKey(), exception);
                continue;
            }
            try {
                fileMapper.physicalDeleteById(orphan.getId());
                removed++;
            } catch (RuntimeException exception) {
                log.warn("孤儿文件元数据删除失败：{}", orphan.getId(), exception);
            }
        }
        return removed;
    }

    @Override
    @Transactional
    public void bind(List<Long> fileIds, String bizType, Long bizId) {
        List<Long> distinctIds = distinctIds(fileIds);
        if (distinctIds.isEmpty()) {
            return;
        }
        if (bizId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "绑定文件时业务主键不能为空");
        }
        String normalizedBizType = trimToNull(bizType);
        if (normalizedBizType == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "绑定文件时业务类型不能为空");
        }
        if (normalizedBizType.length() > MAX_BIZ_TYPE_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "业务类型码过长，最多 " + MAX_BIZ_TYPE_LENGTH + " 个字符");
        }

        AuthenticatedUser user = AuthContext.requireUser();
        for (Long fileId : distinctIds) {
            FileEntity entity = requireFile(fileId);
            assertSameInstitution(entity);
            if (entity.getBizId() != null && !entity.getBizId().equals(bizId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文件「" + entity.getOriginalName() + "」已被其他业务数据引用，请重新上传");
            }
        }

        for (Long fileId : distinctIds) {
            LambdaUpdateWrapper<FileEntity> update = new LambdaUpdateWrapper<>();
            update.eq(FileEntity::getId, fileId)
                .set(FileEntity::getBizType, normalizedBizType)
                .set(FileEntity::getBizId, bizId)
                .set(FileEntity::getUpdatedBy, user.userId());
            fileMapper.update(null, update);
        }
    }

    @Override
    @Transactional
    public void unbind(List<Long> fileIds) {
        List<Long> distinctIds = distinctIds(fileIds);
        if (distinctIds.isEmpty()) {
            return;
        }
        AuthenticatedUser user = AuthContext.requireUser();
        for (Long fileId : distinctIds) {
            FileEntity entity = requireFile(fileId);
            assertSameInstitution(entity);
            LambdaUpdateWrapper<FileEntity> update = new LambdaUpdateWrapper<>();
            update.eq(FileEntity::getId, fileId)
                .set(FileEntity::getBizType, null)
                .set(FileEntity::getBizId, null)
                .set(FileEntity::getUpdatedBy, user.userId());
            fileMapper.update(null, update);
        }
    }

    @Override
    @Transactional
    public void releaseBoundByBiz(String bizType, Long bizId) {
        String normalizedBizType = trimToNull(bizType);
        if (normalizedBizType == null || bizId == null) {
            return;
        }
        LambdaUpdateWrapper<FileEntity> update = new LambdaUpdateWrapper<>();
        update.eq(FileEntity::getBizType, normalizedBizType)
            .eq(FileEntity::getBizId, bizId)
            .set(FileEntity::getBizType, null)
            .set(FileEntity::getBizId, null);
        fileMapper.update(null, update);
    }

    @Override
    public void clearUploader(Long userId) {
        if (userId == null) {
            return;
        }
        LambdaUpdateWrapper<FileEntity> update = new LambdaUpdateWrapper<>();
        update.eq(FileEntity::getUploadedBy, userId)
            .set(FileEntity::getUploadedBy, null);
        fileMapper.update(null, update);
    }

    private FileEntity requireFile(Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件ID不能为空");
        }
        FileEntity entity = fileMapper.selectById(fileId);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在或已被删除");
        }
        return entity;
    }

    /** 租户隔离：客户端传的 fileId 不是通行证，必须与登录态所属机构一致。 */
    private void assertSameInstitution(FileEntity entity) {
        AuthenticatedUser user = AuthContext.requireUser();
        if (isPlatformAdmin(user)) {
            return;
        }
        Long orgId = user.orgId();
        if (orgId == null || !orgId.equals(entity.getInstitutionId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他机构的文件");
        }
    }

    private boolean isPlatformAdmin(AuthenticatedUser user) {
        return user.roles() != null && user.roles().contains(PLATFORM_ADMIN_ROLE);
    }

    private String normalizeBizType(String bizType, Long bizId) {
        String value = trimToNull(bizType);
        if (value != null && value.length() > MAX_BIZ_TYPE_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "业务类型码过长，最多 " + MAX_BIZ_TYPE_LENGTH + " 个字符");
        }
        if (bizId != null && value == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "指定业务主键时必须同时提供业务类型");
        }
        return value;
    }

    /** 对象键只由后端拼装，不含任何用户输入：机构ID/年/月/UUID.扩展名。 */
    private String buildObjectKey(Long institutionId, String extension) {
        LocalDate today = LocalDate.now();
        String suffix = extension == null ? "" : "." + extension;
        return institutionId + "/" + today.getYear() + "/"
            + String.format("%02d", today.getMonthValue()) + "/"
            + UUID.randomUUID() + suffix;
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            fileStorage.delete(objectKey);
        } catch (RuntimeException exception) {
            log.warn("上传回滚：对象删除失败 {}", objectKey, exception);
        }
    }

    private List<Long> distinctIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return fileIds.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private FileView toView(FileEntity entity) {
        return new FileView(
            entity.getId(),
            entity.getOriginalName(),
            entity.getExtension(),
            entity.getContentType(),
            entity.getSizeBytes(),
            entity.getBizType(),
            entity.getBizId(),
            entity.getCreatedAt());
    }

    private FileListView toListView(FileEntity entity) {
        return new FileListView(
            entity.getId(),
            entity.getOriginalName(),
            entity.getExtension(),
            entity.getContentType(),
            entity.getSizeBytes(),
            entity.getBizType(),
            entity.getBizId(),
            entity.getInstitutionId(),
            entity.getUploadedBy(),
            entity.getCreatedAt());
    }
}
