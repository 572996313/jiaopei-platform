package com.cornerstone.module.education.teacher.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.module.education.teacher.dto.TeacherImportResult;
import com.cornerstone.module.education.teacher.service.TeacherImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 教师档案批量导入。归属机构不在参数里：三个端点一律以当前登录账号的机构为准，
 * 由 {@code TeacherImportServiceImpl} 从认证上下文取——与底座的账号批量导入同一口径。
 *
 * <p>这里不创建登录账号：开号是 {@code /api/system/user-import}（权限 {@code system:user:manage}）的职责，
 * 档案与账号的关联走 {@code TeacherController} 的按手机号匹配。</p>
 */
@RestController
@RequestMapping("/api/education/teacher-import")
@Tag(name = "教务管理")
public class TeacherImportController {

    private static final String XLSX_MEDIA_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TeacherImportService teacherImportService;

    public TeacherImportController(TeacherImportService teacherImportService) {
        this.teacherImportService = teacherImportService;
    }

    @Operation(summary = "下载教师档案导入模板",
        description = "返回 xlsx 模板：第一个工作表是要填的数据页，第二个工作表是填写说明；"
            + "性别/用工类型下拉取系统字典当前启用条目")
    @RequiresPermission("education:profile:write")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] workbook = teacherImportService.buildTemplate();
        String filename = URLEncoder.encode("教师档案批量导入模板.xlsx", StandardCharsets.UTF_8)
            .replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
            .body(workbook);
    }

    @Operation(summary = "预览校验教师档案导入文件",
        description = "解析 xlsx 并逐行校验（必填项、手机号格式与本机构占用、校区编码是否存在、"
            + "性别/用工类型是否为字典启用值、入职日期格式），返回逐行结果与错误原因，不写入任何数据。"
            + "归属机构不接参数，一律取当前登录账号所在机构")
    @RequiresPermission("education:profile:write")
    @PostMapping("/preview")
    public TeacherImportResult preview(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return teacherImportService.preview(file);
    }

    @Operation(summary = "确认导入教师档案",
        description = "再次解析校验，任一行不通过则整批拒绝；通过后在当前登录账号所属机构下建档。"
            + "本接口不创建登录账号，账号请到「系统设置 → 用户管理 → 批量导入账号」开，"
            + "再回教师页用「匹配账号」绑定")
    @RequiresPermission("education:profile:write")
    @PostMapping("/commit")
    public TeacherImportResult commit(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return teacherImportService.commit(file);
    }
}
