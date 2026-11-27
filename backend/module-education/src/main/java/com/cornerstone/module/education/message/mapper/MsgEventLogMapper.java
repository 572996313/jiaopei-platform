package com.cornerstone.module.education.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.message.domain.entity.MsgEventLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface MsgEventLogMapper extends BaseMapper<MsgEventLogEntity> {

    /** 保留期清理：事件时间线随事件一起物理删除，不单独留墓碑。 */
    @Delete("<script>DELETE FROM jp_msg_event_log WHERE event_id IN "
        + "<foreach collection='eventIds' item='eventId' open='(' separator=',' close=')'>#{eventId}</foreach></script>")
    int physicalDeleteByEventIds(@Param("eventIds") List<Long> eventIds);
}
