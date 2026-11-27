package com.cornerstone.module.education.fee.service;

import com.cornerstone.module.education.fee.dto.MiniappStudentFeeView;

/**
 * 家长端只读费用查询。
 *
 * <p>与后台台账列表的关键区别：这里**不按调用者机构收窄**，调用方（{@code MiniappService}）
 * 必须先确认该学员已绑定当前账号。家长账号所属机构只是注册时的默认机构，学员档案可以在
 * 机构之间，按机构过滤会把家长本来有权看的台账藏掉。</p>
 */
public interface StudentFeeQueryService {

    /** 某学员的收费概览（台账 + 项目明细 + 收款流水），金额全部现算 */
    MiniappStudentFeeView studentFees(Long studentId);
}
