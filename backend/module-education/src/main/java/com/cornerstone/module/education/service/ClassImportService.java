package com.cornerstone.module.education.service;

import com.cornerstone.module.education.dto.XlsxImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 班级档案批量导入：下载模板 → 预览校验 → 确认建档。
 *
 * <p>归属机构只取当前登录账号；本模板只建班级主档，不建校区/学期/课程/班型/教师。</p>
 */
public interface ClassImportService {

    byte[] buildTemplate();

    XlsxImportResult preview(MultipartFile file);

    XlsxImportResult commit(MultipartFile file);
}
