package com.cornerstone.module.education.fee.support;

import com.cornerstone.core.common.api.ResultCode;
import com.cornerstone.core.common.exception.BusinessException;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import com.cornerstone.module.education.domain.entity.TeachingClassEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import com.cornerstone.module.education.fee.mapper.FeeLedgerItemMapper;
import com.cornerstone.module.education.fee.mapper.FeeLedgerMapper;
import com.cornerstone.module.education.mapper.StudentMapper;
import com.cornerstone.module.education.mapper.TeachingClassMapper;
import com.cornerstone.module.organization.domain.entity.TermEntity;
import com.cornerstone.module.organization.mapper.TermMapper;
import com.cornerstone.module.organization.service.support.CampusScopeGuard;
import com.cornerstone.module.organization.service.support.InstitutionScopeGuard;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 收费台账的数据归属收口：机构、校区范围与"子行必须属于该台账"的校验只写在这里。
 *
 * <p>列表、详情、收款、逐月、出勤、导出六条链路都从这里过一遍，避免某一处漏了
 * {@code assertCampus} 之后"别的校区的钱能改、但列表里看不到"这种半隔离状态。</p>
 */
@Component
public class FeeLedgerGuard {

    private final FeeLedgerMapper feeLedgerMapper;
    private final FeeLedgerItemMapper feeLedgerItemMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final TermMapper termMapper;
    private final StudentMapper studentMapper;

    public FeeLedgerGuard(FeeLedgerMapper feeLedgerMapper, FeeLedgerItemMapper feeLedgerItemMapper,
                          TeachingClassMapper teachingClassMapper, TermMapper termMapper,
                          StudentMapper studentMapper) {
        this.feeLedgerMapper = feeLedgerMapper;
        this.feeLedgerItemMapper = feeLedgerItemMapper;
        this.teachingClassMapper = teachingClassMapper;
        this.termMapper = termMapper;
        this.studentMapper = studentMapper;
    }

    public FeeLedgerEntity requireLedger(Long ledgerId) {
        FeeLedgerEntity ledger = feeLedgerMapper.selectById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收费台账不存在：" + ledgerId);
        }
        InstitutionScopeGuard.assertOwn(ledger.getInstitutionId(), "收费台账");
        CampusScopeGuard.assertCampus(ledger.getCampusId(), "收费台账");
        return ledger;
    }

    public FeeLedgerItemEntity requireItem(Long ledgerItemId) {
        FeeLedgerItemEntity item = feeLedgerItemMapper.selectById(ledgerItemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收费项目行不存在：" + ledgerItemId);
        }
        requireLedger(item.getLedgerId());
        return item;
    }

    public Long resolveInstitution(Long requested) {
        Long institutionId = InstitutionScopeGuard.resolveForQuery(requested, "收费台账");
        if (institutionId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "机构不能为空");
        }
        return institutionId;
    }

    public TeachingClassEntity requireClass(Long classId, Long institutionId) {
        TeachingClassEntity clazz = teachingClassMapper.selectById(classId);
        if (clazz == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "班级不存在：" + classId);
        }
        if (!Objects.equals(clazz.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "班级不属于该机构：" + clazz.getClassName());
        }
        CampusScopeGuard.assertCampus(clazz.getCampusId(), "收费台账");
        return clazz;
    }

    public TermEntity requireTerm(Long termId, Long institutionId) {
        TermEntity term = termMapper.selectById(termId);
        if (term == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学期不存在：" + termId);
        }
        if (!Objects.equals(term.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学期不属于该机构：" + term.getTermName());
        }
        if (term.getStartDate() == null || term.getEndDate() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                "学期「" + term.getTermName() + "」未维护起止日期，无法生成收费月表");
        }
        return term;
    }

    public StudentEntity requireStudent(Long studentId, Long institutionId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "学员不存在：" + studentId);
        }
        if (!Objects.equals(student.getInstitutionId(), institutionId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "学员不属于该机构：" + student.getStudentName());
        }
        return student;
    }
}
