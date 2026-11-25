package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.module.education.dto.XlsxImportResult;
import com.cornerstone.module.education.service.ClassStudentImportService;
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
 * 分班（班级学员关系）批量导入。归属机构不在参数里：三个端点一律以当前登录账号的机构为准。
 *
 * <p>入班即报名，与 {@code POST /api/education/class-students} 是同一个写入口径；
 * 这里不创建学员档案，学员请先用「学员管理」页的批量导入建好。</p>
 */
@RestController
@RequestMapping("/api/education/class-student-import")
@Tag(name = "教务管理")
public class ClassStudentImportController {

    private static final String XLSX_MEDIA_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ClassStudentImportService classStudentImportService;

    public ClassStudentImportController(ClassStudentImportService classStudentImportService) {
        this.classStudentImportService = classStudentImportService;
    }

    @Operation(summary = "下载分班导入模板",
        description = "返回 xlsx 模板：第一个工作表是要填的数据页，第二个工作表是填写说明；"
            + "来源渠道下拉取系统字典 enrollment_channel 当前启用条目")
    @RequiresPermission("education:class:write")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] workbook = classStudentImportService.buildTemplate();
        String filename = URLEncoder.encode("分班批量导入模板.xlsx", StandardCharsets.UTF_8)
            .replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
            .body(workbook);
    }

    @Operation(summary = "预览校验分班导入文件",
        description = "解析 xlsx 并逐行校验（班级编码与学号必须存在、学员姓名核对、来源渠道字典值、"
            + "入班日期格式、同文件重复、已在班关系、班级容量），返回逐行结果与错误原因，"
            + "不写入任何数据。归属机构不接参数，一律取当前登录账号所在机构")
    @RequiresPermission("education:class:write")
    @PostMapping("/preview")
    public XlsxImportResult preview(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return classStudentImportService.preview(file);
    }

    @Operation(summary = "确认导入分班",
        description = "再次解析校验，任一行不通过则整批拒绝；通过后逐行走新增入班。"
            + "已在班的关系跳过（幂等），结果里回报新建与跳过条数")
    @RequiresPermission("education:class:write")
    @PostMapping("/commit")
    public XlsxImportResult commit(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return classStudentImportService.commit(file);
    }
}
