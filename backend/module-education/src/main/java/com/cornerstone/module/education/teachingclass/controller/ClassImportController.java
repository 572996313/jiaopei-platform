package com.cornerstone.module.education.teachingclass.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.module.education.dto.XlsxImportResult;
import com.cornerstone.module.education.teachingclass.service.ClassImportService;
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
 * 班级档案批量导入。归属机构不在参数里：三个端点一律以当前登录账号的机构为准。
 *
 * <p>只建班级主档，不建校区/学期/课程/班型/教师；那些编码先查好再导入。</p>
 */
@RestController
@RequestMapping("/api/education/class-import")
@Tag(name = "教务管理")
public class ClassImportController {

    private static final String XLSX_MEDIA_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ClassImportService classImportService;

    public ClassImportController(ClassImportService classImportService) {
        this.classImportService = classImportService;
    }

    @Operation(summary = "下载班级档案导入模板",
        description = "返回 xlsx 模板：第一个工作表是要填的数据页，第二个工作表是填写说明；"
            + "班级状态下拉为代码判定值 planning/active/inactive/closed，含义见说明页")
    @RequiresPermission("education:class:write")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] workbook = classImportService.buildTemplate();
        String filename = URLEncoder.encode("班级档案批量导入模板.xlsx", StandardCharsets.UTF_8)
            .replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
            .body(workbook);
    }

    @Operation(summary = "预览校验班级档案导入文件",
        description = "解析 xlsx 并逐行校验（班级名称必填、校区编码必填且在本账号可访问范围内、"
            + "学期/课程/班型/主讲教师编码是否存在、同校区班级编码与名称是否重复、日期与数字格式），"
            + "返回逐行结果与错误原因，不写入任何数据。归属机构不接参数，一律取当前登录账号所在机构")
    @RequiresPermission("education:class:write")
    @PostMapping("/preview")
    public XlsxImportResult preview(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return classImportService.preview(file);
    }

    @Operation(summary = "确认导入班级档案",
        description = "再次解析校验，任一行不通过则整批拒绝；通过后在当前登录账号所属机构下建档。"
            + "班级编码留空的行按编号规则自动生成，导入结果里回填实际编码；同校区同编码已存在则跳过")
    @RequiresPermission("education:class:write")
    @PostMapping("/commit")
    public XlsxImportResult commit(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return classImportService.commit(file);
    }
}
