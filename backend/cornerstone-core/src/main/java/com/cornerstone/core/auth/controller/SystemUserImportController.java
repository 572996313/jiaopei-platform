package com.cornerstone.core.auth.controller;

import com.cornerstone.core.auth.annotation.RequiresPermission;
import com.cornerstone.core.auth.dto.UserAccountImportResult;
import com.cornerstone.core.auth.service.UserAccountImportService;
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
 * 用户账号批量导入。归属机构不在参数里：三个端点一律以当前登录账号的机构为准，
 * 由 {@code UserAccountImportServiceImpl} 从认证上下文取。
 */
@RestController
@RequestMapping("/api/system/user-import")
@Tag(name = "用户账号批量导入")
public class SystemUserImportController {

    private static final String XLSX_MEDIA_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final UserAccountImportService userAccountImportService;

    public SystemUserImportController(UserAccountImportService userAccountImportService) {
        this.userAccountImportService = userAccountImportService;
    }

    @Operation(summary = "下载账号导入模板",
        description = "返回 xlsx 模板：第一个工作表是要填的数据页，第二个工作表是填写说明；状态列带下拉校验")
    @RequiresPermission("system:user:manage")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] workbook = userAccountImportService.buildTemplate();
        String filename = URLEncoder.encode("账号批量导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
            .body(workbook);
    }

    @Operation(summary = "预览校验账号导入文件",
        description = "解析 xlsx 并逐行校验（必填项、初始口令是否满足密码策略、手机号格式、角色编码是否存在、"
            + "登录账号是否已被占用），返回逐行结果与错误原因，不写入任何数据。"
            + "归属机构不接参数，一律取当前登录账号所在机构")
    @RequiresPermission("system:user:manage")
    @PostMapping("/preview")
    public UserAccountImportResult preview(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return userAccountImportService.preview(file);
    }

    @Operation(summary = "确认导入账号",
        description = "再次解析校验，任一行不通过则整批拒绝；通过后在当前登录账号所属机构下新建账号并绑定角色，"
            + "新账号带首次登录强制改密标记")
    @RequiresPermission("system:user:manage")
    @PostMapping("/commit")
    public UserAccountImportResult commit(
        @Parameter(description = "填写完成的导入文件，仅支持 xlsx，内容须在第一个工作表")
        @RequestParam("file") MultipartFile file) {
        return userAccountImportService.commit(file);
    }
}
