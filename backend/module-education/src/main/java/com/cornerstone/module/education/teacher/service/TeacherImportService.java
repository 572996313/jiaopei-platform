package com.cornerstone.module.education.teacher.service;

import com.cornerstone.module.education.teacher.dto.TeacherImportResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * 教师档案批量导入：下载模板 → 预览校验 → 确认建档。
 *
 * <p>只建档案、不建登录账号。开账号是底座 {@code /api/system/user-import} 的职责
 * （权限 {@code system:user:manage}），档案与账号的绑定走
 * {@link TeacherAccountLinkService} 的按手机号匹配。</p>
 */
public interface TeacherImportService {

    /** 生成 xlsx 模板 */
    byte[] buildTemplate();

    /** 逐行校验，不落库 */
    TeacherImportResult preview(MultipartFile file);

    /** 再校验一次，任一行不通过整批拒绝；通过后建档 */
    TeacherImportResult commit(MultipartFile file);
}
