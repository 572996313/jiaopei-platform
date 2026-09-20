package com.cornerstone.module.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TimeSlotMapper extends BaseMapper<TimeSlotEntity> {

    /** 统计未删除课次对该时间段的引用数(schedule_lesson 在 education 模块,这里直查表避免包循环依赖) */
    @Select("SELECT COUNT(1) FROM schedule_lesson WHERE time_slot_id = #{timeSlotId} AND deleted = 0")
    long countLessonRefs(@Param("timeSlotId") Long timeSlotId);

    /** 物理删除(绕过逻辑删除):时间段唯一键含 deleted,软删残留会永久占用编码/名称 */
    @Delete("DELETE FROM jp_time_slot WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);
}
