package com.cornerstone.core.auth.service;

import com.cornerstone.core.auth.dto.UserAccountImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户账号批量导入：下载模板 → 预览校验 → 确认新建。
 *
 * <p>归属机构恒取当前登录账号，不接受文件里或请求里的机构参数；只新建账号，
 * 不批量改资料。理由见 {@code UserAccountImportServiceImpl} 顶部的三条硬口径。</p>
 */
public interface UserAccountImportService {

    byte[] buildTemplate();

    UserAccountImportResult preview(MultipartFile file);

    UserAccountImportResult commit(MultipartFile file);
}
