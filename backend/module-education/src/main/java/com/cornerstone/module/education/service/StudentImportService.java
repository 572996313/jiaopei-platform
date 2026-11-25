package com.cornerstone.module.education.service;

import com.cornerstone.module.education.dto.XlsxImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学员档案批量导入：下载模板 → 预览校验 → 确认建档。
 *
 * <p>归属机构只取当前登录账号，接口没有 institutionId 参数；本模板不创建登录账号。</p>
 */
public interface StudentImportService {

    byte[] buildTemplate();

    XlsxImportResult preview(MultipartFile file);

    XlsxImportResult commit(MultipartFile file);
}
