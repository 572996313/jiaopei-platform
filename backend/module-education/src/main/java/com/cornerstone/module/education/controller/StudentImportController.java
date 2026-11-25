package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.module.education.dto.XlsxImportResult;
import com.cornerstone.module.education.service.StudentImportService;
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
 * 学员档案批量导入。归属机构不在参数里：三个端点一律以当前登录账号的机构为准，
 * 与教师导入、底座的账号批量导入同一口径。
 *
 * <p>这里不创建登录账号：学员/家长的小程序账号走自助注册，或到用户管理页开通后绑定。</p>
 */
@RestController
@RequestMapping("/api/education/student-import")
@Tag(name = "教务管理")
public class StudentImportController {

    private static final String XLSX_MEDIA_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final StudentImportService studentImportService;

    public StudentImportController(StudentImportService studentImportService) {
        this.studentImportService = studentImportService;
    }

    @Operation(summary = "下载学员档案导入模板",
        description = "返回 xlsx 模板：第一个工作表是要填的数据页，第二个工作表是填写说明；"
            + "性别/学员状态下拉取系统字典当前启用条目")
    @RequiresPermission("education:profile:write")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] workbook = studentImportService.buildTemplate();
        String filename = URLEncoder.encode("学员档案批量导入模板.xlsx", StandardCharsets.UTF_8)
            .replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
            .body(workbook);
    }

    @Operation(summary = "预览校验学员档案导入文件",
        description = "解析 xlsx 并逐行校验（姓名必填、日期格式与不可晚于今天、"
            + "性别/学员状态是否为字典启用值、学号是否本机构重复），返回逐行结果与错误原因，"
            + "不写入任何数据。归属机构不接参数，一律取当前登录账号所在机构")
    @RequiresPermission("education:profile:write")
    @PostMapping("/preview")
    public XlsxImportResult preview(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return studentImportService.preview(file);
    }

    @Operation(summary = "确认导入学员档案",
        description = "再次解析校验，任一行不通过则整批拒绝；通过后在当前登录账号所属机构下建档。"
            + "学号留空的行按编号规则自动生成，导入结果里回填实际学号。本接口不创建登录账号")
    @RequiresPermission("education:profile:write")
    @PostMapping("/commit")
    public XlsxImportResult commit(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return studentImportService.commit(file);
    }
}
