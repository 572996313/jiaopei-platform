package com.cornerstone.module.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.organization.domain.entity.TimeSlotEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TimeSlotMapper extends BaseMapper<TimeSlotEntity> {

    /**
     * 统计对该时间段有外键引用的课次数(schedule_lesson 在 education 模块,这里直查表避免包循环依赖)。
     * 必须含软删行:课次逻辑删除后行仍留在表里,外键照旧指向本节次,漏判会让物理删除被数据库拒绝(1451)。
     */
    @Select("SELECT COUNT(1) FROM schedule_lesson WHERE time_slot_id = #{timeSlotId}")
    long countLessonRefs(@Param("timeSlotId") Long timeSlotId);

    /**
     * 其中"还在排课里"的引用数(未逻辑删除)。
     * 与 {@link #countLessonRefs} 的差就是已删除课次留下的引用——这类可以在节次明细里清理掉，清理后本节次就能真删。
     */
    @Select("SELECT COUNT(1) FROM schedule_lesson WHERE time_slot_id = #{timeSlotId} AND deleted = 0")
    long countLiveLessonRefs(@Param("timeSlotId") Long timeSlotId);

    /** 物理删除(绕过逻辑删除):时间段唯一键含 deleted,软删残留会永久占用编码/名称 */
    @Delete("DELETE FROM jp_time_slot WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);
}
