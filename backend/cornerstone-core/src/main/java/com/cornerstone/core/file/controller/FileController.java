package com.cornerstone.core.file.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.common.api.ApiResponse;
import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.core.file.dto.FileContent;
import com.cornerstone.core.file.dto.FileListView;
import com.cornerstone.core.file.dto.FilePageQuery;
import com.cornerstone.core.file.dto.FileView;
import com.cornerstone.core.file.service.FileService;
import com.cornerstone.core.file.service.support.FileMediaTypes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件接口。
 *
 * <p>业务表只保存上传返回的 {@code fileId}，不要保存对象键或 URL。读取走
 * {@code /content}，服务端按 fileId 现查元数据并校验机构。</p>
 */
@RestController
@RequestMapping("/api/system/files")
@Tag(name = "文件管理", description = "通用文件上传、下载与元数据管理")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    @RequiresPermission("system:file:upload")
    @Operation(summary = "上传文件", description = "multipart 提交；归属机构取当前登录态，不接受调用方指定")
    public FileView upload(
            @Parameter(description = "文件") @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "业务类型码(可选，如 student_photo)") @RequestParam(required = false) String bizType,
            @Parameter(description = "业务主键(可选，与业务类型同时提供)") @RequestParam(required = false) Long bizId) {
        return fileService.upload(file, bizType, bizId);
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "获取文件元数据", description = "只返回 fileId 与元数据，不返回对象键与桶名")
    public FileView getFile(@Parameter(description = "文件ID") @PathVariable Long fileId) {
        return fileService.getFile(fileId);
    }

    @GetMapping("/{fileId}/content")
    @Operation(summary = "获取文件内容",
        description = "图片、PDF、纯文本默认内联预览；download=true 或其余类型强制附件下载")
    public ResponseEntity<Resource> content(
            @Parameter(description = "文件ID") @PathVariable Long fileId,
            @Parameter(description = "是否强制下载") @RequestParam(defaultValue = "false") boolean download) {
        FileContent content = fileService.openContent(fileId);
        String contentType = content.contentType() == null
            ? FileMediaTypes.OCTET_STREAM : content.contentType();
        boolean inline = !download && FileMediaTypes.inlineSafe(contentType);
        String encodedName = URLEncoder.encode(content.fileName(), StandardCharsets.UTF_8)
            .replace("+", "%20");
        String disposition = (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encodedName;
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .contentLength(content.sizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
            // 内容类型由扩展名推导，禁止浏览器再嗅探：否则改名的 HTML 可能被当成页面执行
            .header("X-Content-Type-Options", "nosniff")
            .body(new InputStreamResource(content.stream()));
    }

    @GetMapping("/page")
    @RequiresPermission("system:file:manage")
    @Operation(summary = "文件分页列表", description = "支持文件名、业务类型、上传时间筛选")
    public PageResult<FileListView> page(@Valid @ParameterObject FilePageQuery query) {
        return fileService.pageFiles(query);
    }

    @DeleteMapping("/{fileId}")
    @RequiresPermission("system:file:manage")
    @Operation(summary = "删除文件", description = "已被业务数据引用的文件不允许直接删除")
    public ApiResponse<Void> delete(@Parameter(description = "文件ID") @PathVariable Long fileId) {
        fileService.deleteFile(fileId);
        return ApiResponse.success("文件已删除", null);
    }

    @PostMapping("/cleanup-orphans")
    @RequiresPermission("system:file:manage")
    @Operation(summary = "清理孤儿文件", description = "删除超过保留时长且未绑定业务的文件，返回清理条数")
    public Integer cleanupOrphans() {
        return fileService.cleanupOrphans();
    }
}
