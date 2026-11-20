package com.cornerstone.module.education.fee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.fee.domain.entity.FeeItemEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface FeeItemMapper extends BaseMapper<FeeItemEntity> {

    /**
     * 收费项目是"删了还想重建同编码"的档案表，唯一键含 deleted 但只容得下一个墓碑，
     * 所以删除入口先校验无引用，再物理删。调用方负责引用校验（价目 / 台账明细）。
     */
    @Delete("DELETE FROM jp_fee_item WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
