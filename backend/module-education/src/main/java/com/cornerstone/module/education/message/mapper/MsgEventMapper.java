package com.cornerstone.module.education.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.message.domain.entity.MsgEventEntity;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface MsgEventMapper extends BaseMapper<MsgEventEntity> {

    /**
     * 保留期清理用的物理删除。
     *
     * <p>唯一键 {@code uk_jp_msg_event_dedupe(institution_id, type_code, dedupe_key, deleted)}
     * 里 deleted 是 0/1 二值，逻辑删除只会留下一条墓碑；同一去重键（如同一课次）第二年再次
     * 过期清理时必撞唯一键。事件是过期历史、没有重建同键的管理入口，按 AGENTS
     * 「逻辑删除唯一键收口」直接物理删除。</p>
     */
    @Delete("<script>DELETE FROM jp_msg_event WHERE id IN "
        + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int physicalDeleteByIds(@Param("ids") List<Long> ids);
}
