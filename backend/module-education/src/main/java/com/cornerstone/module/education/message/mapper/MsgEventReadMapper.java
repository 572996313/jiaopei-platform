package com.cornerstone.module.education.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.message.domain.entity.MsgEventReadEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MsgEventReadMapper extends BaseMapper<MsgEventReadEntity> {

    /** 某类型事件的去重已读人数（阅读台账用）。 */
    @Select("SELECT COUNT(DISTINCT r.user_id) FROM jp_msg_event_read r "
        + "JOIN jp_msg_event e ON e.id = r.event_id AND e.deleted = 0 "
        + "WHERE r.deleted = 0 AND e.type_code = #{typeCode}")
    long countDistinctUsersByTypeCode(@Param("typeCode") String typeCode);

    /** 某事件的阅读人列表；账号被删时姓名回退为空，不丢已读时间。 */
    @Select("SELECT r.user_id AS userId, u.display_name AS userName, r.read_at AS readAt "
        + "FROM jp_msg_event_read r LEFT JOIN sys_user_account u ON u.id = r.user_id "
        + "WHERE r.event_id = #{eventId} AND r.deleted = 0 ORDER BY r.read_at ASC")
    List<Map<String, Object>> listReaders(@Param("eventId") Long eventId);

    /** 保留期清理：随事件一起物理删除，避免 (user_id, event_id, deleted) 攒出第二条墓碑。 */
    @Delete("<script>DELETE FROM jp_msg_event_read WHERE event_id IN "
        + "<foreach collection='eventIds' item='eventId' open='(' separator=',' close=')'>#{eventId}</foreach></script>")
    int physicalDeleteByEventIds(@Param("eventIds") List<Long> eventIds);
}
