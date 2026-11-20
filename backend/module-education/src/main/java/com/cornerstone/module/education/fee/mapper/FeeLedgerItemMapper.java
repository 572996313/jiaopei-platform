package com.cornerstone.module.education.fee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerItemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface FeeLedgerItemMapper extends BaseMapper<FeeLedgerItemEntity> {

    /** 台账明细无收款、无逐月发生额时才允许物理删除，调用方负责校验。 */
    @Delete("DELETE FROM jp_fee_ledger_item WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /** 台账物理删除前清子行（外键要求）；调用方已保证没有收款。 */
    @Delete("DELETE FROM jp_fee_ledger_item WHERE ledger_id = #{ledgerId}")
    int deletePhysicallyByLedgerId(@Param("ledgerId") Long ledgerId);
}
