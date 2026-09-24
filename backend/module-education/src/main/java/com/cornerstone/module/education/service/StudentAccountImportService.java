package com.cornerstone.module.education.service;

import com.cornerstone.module.education.dto.StudentAccountActivationRequest;
import com.cornerstone.module.education.dto.StudentAccountActivationView;
import com.cornerstone.module.education.dto.StudentAccountImportResult;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学员/家长登录账号的批量导入。
 *
 * <p>导入只建「待激活」账号并绑定学员档案，账号密码由家长本人凭激活码自设，
 * 因此本服务不产出任何可用口令。放业务模块而不是底座：模板列是学号/家长这类
 * 行业词汇，且绑定关系写在 {@code jp_user_student_rel}，底座不感知。</p>
 */
public interface StudentAccountImportService {

    byte[] buildTemplate();

    /**
     * 导出「还没有登录账号」的学员名册，前两列预填，供业务员补完手机号后直接回传导入。
     *
     * @param campusId 可选，只导某个校区；名册行数超过单次导入上限时要求按校区分批
     */
    byte[] buildRoster(Long institutionId, Long campusId);

    /** 解析并逐行校验，不写任何数据 */
    StudentAccountImportResult preview(MultipartFile file, Long institutionId);

    /** 再解析校验一次，全部通过才落库；任一行不通过整批拒绝 */
    StudentAccountImportResult commit(MultipartFile file, Long institutionId);

    List<StudentAccountActivationView> createActivationCodes(StudentAccountActivationRequest request,
                                                             Long institutionId);
}
