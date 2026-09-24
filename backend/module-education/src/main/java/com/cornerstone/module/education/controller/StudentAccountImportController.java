package com.cornerstone.module.education.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.module.education.dto.StudentAccountActivationRequest;
import com.cornerstone.module.education.dto.StudentAccountActivationView;
import com.cornerstone.module.education.dto.StudentAccountImportResult;
import com.cornerstone.module.education.service.StudentAccountImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/education/student-account-import")
@Tag(name = "账号批量导入")
public class StudentAccountImportController {

    private static final String XLSX_MEDIA_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final StudentAccountImportService studentAccountImportService;

    public StudentAccountImportController(StudentAccountImportService studentAccountImportService) {
        this.studentAccountImportService = studentAccountImportService;
    }

    @Operation(summary = "下载账号导入模板",
        description = "返回 xlsx 模板：第一个工作表是要填的数据页，第二个工作表是填写说明；账号类型列带下拉校验")
    @RequiresPermission("education:write")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] workbook = studentAccountImportService.buildTemplate();
        String filename = URLEncoder.encode("账号批量导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
            .body(workbook);
    }

    @Operation(summary = "下载待开账号学员名册",
        description = "导出本机构尚未绑定任何登录账号的学员，学号与姓名两列预填，业务员补齐账号类型/账号姓名/手机号后"
            + "直接回传到「确认导入」，不用再自己抄学号；超过单次导入上限时按校区分批导")
    @RequiresPermission("education:write")
    @GetMapping("/roster")
    public ResponseEntity<byte[]> roster(
        @Parameter(description = "归属机构ID") @RequestParam Long institutionId,
        @Parameter(description = "只导某个校区，不填为全机构") @RequestParam(required = false) Long campusId) {
        byte[] workbook = studentAccountImportService.buildRoster(institutionId, campusId);
        String filename = URLEncoder.encode("待开账号学员名册.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
            .body(workbook);
    }

    @Operation(summary = "预览校验账号导入文件",
        description = "解析 xlsx 并逐行校验（学员编号是否存在、手机号格式、账号是否已被占用、绑定是否冲突），"
            + "返回逐行结果与错误原因，不写入任何数据")
    @RequiresPermission("education:write")
    @PostMapping("/preview")
    public StudentAccountImportResult preview(@RequestParam("file") MultipartFile file,
                                              @Parameter(description = "归属机构ID") @RequestParam Long institutionId) {
        return studentAccountImportService.preview(file, institutionId);
    }

    @Operation(summary = "确认导入账号",
        description = "再次解析校验，任一行不通过则整批拒绝；通过后建待激活账号并绑定学员档案，密码由本人凭激活码自设")
    @RequiresPermission("education:write")
    @PostMapping("/commit")
    public StudentAccountImportResult commit(@RequestParam("file") MultipartFile file,
                                             @Parameter(description = "归属机构ID") @RequestParam Long institutionId) {
        return studentAccountImportService.commit(file, institutionId);
    }

    @Operation(summary = "批量生成激活码",
        description = "为勾选的待激活账号批量发码，同一账号重复申请复用仍在有效期内的原码；已激活或状态不符的账号跳过并在结果里说明")
    @RequiresPermission("education:write")
    @PostMapping("/activation-codes")
    public List<StudentAccountActivationView> activationCodes(
        @Valid @RequestBody StudentAccountActivationRequest request,
        @Parameter(description = "归属机构ID") @RequestParam Long institutionId) {
        return studentAccountImportService.createActivationCodes(request, institutionId);
    }
}
