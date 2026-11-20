package com.cornerstone.module.education.fee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.fee.domain.entity.FeePriceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface FeePriceMapper extends BaseMapper<FeePriceEntity> {

    /** 价目是"同一组维度改完再建"的配置行，删除后同键要能重建，走物理删除。 */
    @Delete("DELETE FROM jp_fee_price WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
