package com.cornerstone.module.education.fee.service;

import com.cornerstone.core.common.api.PageResult;
import com.cornerstone.module.education.fee.dto.FeeLedgerCreateRequest;
import com.cornerstone.module.education.fee.dto.FeeLedgerDetailView;
import com.cornerstone.module.education.fee.dto.FeeLedgerGenerateRequest;
import com.cornerstone.module.education.fee.dto.FeeLedgerGenerateResult;
import com.cornerstone.module.education.fee.dto.FeeLedgerPageQuery;
import com.cornerstone.module.education.fee.dto.FeeLedgerRowView;
import com.cornerstone.module.education.fee.dto.FeeLedgerUpdateRequest;
import com.cornerstone.module.education.fee.dto.FeeRecalculateRequest;

/** 学员收费台账：建账、维护、重算与导出。 */
public interface FeeLedgerService {

    PageResult<FeeLedgerRowView> pageLedgers(FeeLedgerPageQuery query);

    FeeLedgerDetailView getLedger(Long id);

    FeeLedgerDetailView createLedger(FeeLedgerCreateRequest request);

    FeeLedgerDetailView updateLedger(Long id, FeeLedgerUpdateRequest request);

    void deleteLedger(Long id);

    FeeLedgerGenerateResult generate(FeeLedgerGenerateRequest request);

    /** 按当前价目表重新取价并重算逐月发生额（已手工覆盖的发生额不动）。 */
    FeeLedgerDetailView resolvePrices(Long id);

    /** 按当前出勤与标准重算逐月发生额；ledgerId 优先，否则按 termId + classId 批量。 */
    int recalculate(FeeRecalculateRequest request);

    /** 1:1 矩阵表（POI 手工画，含合并单元格与小计/总计）。 */
    byte[] exportMatrix(FeeLedgerPageQuery query);

    /** 一维明细（两张 sheet：台账明细 / 逐月发生）。 */
    byte[] exportDetail(FeeLedgerPageQuery query);
}
