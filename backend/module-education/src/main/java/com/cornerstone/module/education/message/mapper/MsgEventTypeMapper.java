package com.cornerstone.module.education.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.message.domain.entity.MsgEventTypeEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface MsgEventTypeMapper extends BaseMapper<MsgEventTypeEntity> {

    /**
     * 巡检结果写回。用显式 UPDATE 而不是 updateById：
     * last_error 成功时必须从有值清成 NULL，updateById 默认跳过 null 会留下上一轮的假故障。
     */
    @Update("UPDATE jp_msg_event_type SET last_run_at = #{lastRunAt}, last_run_count = #{count}, "
        + "last_error = #{error}, updated_at = NOW() WHERE id = #{id} AND deleted = 0")
    int updateRunResult(@Param("id") Long id,
                        @Param("lastRunAt") java.time.LocalDateTime lastRunAt,
                        @Param("count") int count,
                        @Param("error") String error);
}
