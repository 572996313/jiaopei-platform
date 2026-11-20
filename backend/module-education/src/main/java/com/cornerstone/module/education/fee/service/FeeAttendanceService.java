package com.cornerstone.module.education.fee.service;

import com.cornerstone.module.education.fee.dto.FeeAttendanceOverrideRequest;
import com.cornerstone.module.education.fee.dto.FeeAttendanceSyncRequest;
import com.cornerstone.module.education.fee.dto.FeeAttendanceSyncResult;
import com.cornerstone.module.education.fee.dto.FeeAttendanceView;
import java.time.LocalDate;
import java.util.List;

/** 月度出勤：查询、手工覆盖、从考勤点名汇总。 */
public interface FeeAttendanceService {

    List<FeeAttendanceView> listByLedger(Long ledgerId);

    FeeAttendanceView override(Long ledgerId, LocalDate billingMonth, FeeAttendanceOverrideRequest request);

    /** 撤销手工覆盖，出勤天数回到最近一次考勤汇总值。 */
    FeeAttendanceView revert(Long ledgerId, LocalDate billingMonth);

    FeeAttendanceSyncResult sync(FeeAttendanceSyncRequest request);
}
