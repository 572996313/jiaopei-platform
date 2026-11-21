package com.cornerstone.module.education.fee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.fee.domain.entity.FeePaymentEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FeePaymentMapper extends BaseMapper<FeePaymentEntity> {

    /**
     * 机构当天已用过的最大序号，**连软删墓碑一起算**：收款是财务凭证，单号删掉也不回收，
     * 否则「删除 → 再登记」会把同一个号发给两张单据。MyBatis-Plus 的 selectCount 会自动补
     * deleted = 0，这里必须走原生 SQL 才能把墓碑算进来；尾段不是纯数字的手工单号 CAST 后为 0，
     * 不干扰取号。
     */
    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(payment_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) "
        + "FROM jp_fee_payment WHERE institution_id = #{institutionId} "
        + "AND payment_no LIKE CONCAT(#{prefix}, '%')")
    long maxPaymentNoSequence(@Param("institutionId") Long institutionId, @Param("prefix") String prefix);

    /** 台账物理删除前清收款墓碑（外键要求）；未删除的收款由调用方先拦截，这里只碰 deleted = 1。 */
    @Delete("DELETE FROM jp_fee_payment WHERE ledger_id = #{ledgerId} AND deleted = 1")
    int deleteTombstonesByLedgerId(@Param("ledgerId") Long ledgerId);

    /** 台账明细物理删除前清收款墓碑（外键要求）；未删除的收款由调用方先拦截。 */
    @Delete("DELETE FROM jp_fee_payment WHERE ledger_item_id = #{ledgerItemId} AND deleted = 1")
    int deleteTombstonesByLedgerItemId(@Param("ledgerItemId") Long ledgerItemId);
}
