package com.cornerstone.module.education.fee.service;

import com.cornerstone.module.education.fee.dto.FeeMonthlyOverrideRequest;
import com.cornerstone.module.education.fee.dto.FeeMonthlyView;
import java.time.LocalDate;
import java.util.List;

/** 逐月发生额查询与手工覆盖。 */
public interface FeeMonthlyService {

    List<FeeMonthlyView> listByLedger(Long ledgerId);

    FeeMonthlyView override(Long ledgerItemId, LocalDate billingMonth, FeeMonthlyOverrideRequest request);

    /** 撤销手工覆盖：月份系数与发生额回到引擎默认口径。 */
    FeeMonthlyView revert(Long ledgerItemId, LocalDate billingMonth);
}
