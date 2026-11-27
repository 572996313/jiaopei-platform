package com.cornerstone.module.education.teachingclass.service;

import com.cornerstone.module.education.dto.XlsxImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分班（班级学员关系）批量导入：下载模板 → 预览校验 → 确认入班。
 *
 * <p>入班即报名，与 {@code POST /api/education/class-students} 是同一个写入口径；
 * 已在班的关系跳过而不是报错，重传整份文件不会整批失败。</p>
 */
public interface ClassStudentImportService {

    byte[] buildTemplate();

    XlsxImportResult preview(MultipartFile file);

    XlsxImportResult commit(MultipartFile file);
}
