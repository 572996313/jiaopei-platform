package com.cornerstone.module.education.fee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.fee.domain.entity.FeeMonthlyEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface FeeMonthlyMapper extends BaseMapper<FeeMonthlyEntity> {

    /** 台账物理删除前清子行（外键要求）；调用方已保证没有收款。 */
    @Delete("DELETE FROM jp_fee_monthly WHERE ledger_id = #{ledgerId}")
    int deletePhysicallyByLedgerId(@Param("ledgerId") Long ledgerId);

    /** 台账明细移除前清子行；调用方已保证该项目没有手工覆盖。 */
    @Delete("DELETE FROM jp_fee_monthly WHERE ledger_item_id = #{ledgerItemId}")
    int deletePhysicallyByLedgerItemId(@Param("ledgerItemId") Long ledgerItemId);
}
