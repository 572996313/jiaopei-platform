package com.jiaopei.platform.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiaopei.platform.organization.domain.entity.TimeSlotTemplateEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface TimeSlotTemplateMapper extends BaseMapper<TimeSlotTemplateEntity> {

    /** 物理删除(绕过逻辑删除):模板唯一键含 deleted,软删残留会永久占用模板名称 */
    @Delete("DELETE FROM jp_time_slot_template WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);
}
