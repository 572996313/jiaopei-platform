package com.cornerstone.module.education.fee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.fee.domain.entity.FeeLedgerEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface FeeLedgerMapper extends BaseMapper<FeeLedgerEntity> {

    /** 台账无收款、无逐月发生额时才允许物理删除，调用方负责校验。 */
    @Delete("DELETE FROM jp_fee_ledger WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
