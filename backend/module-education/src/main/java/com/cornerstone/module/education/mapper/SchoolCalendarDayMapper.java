package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.SchoolCalendarDayEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface SchoolCalendarDayMapper extends BaseMapper<SchoolCalendarDayEntity> {

    // uk_jp_school_calendar_day (institution_id, campus_id, day_date, deleted) 里的 deleted 是 0/1 二值，
    // 逻辑删除会让"今年登记→撤销→明年同一天再登记"撞在墓碑上，按 AGENTS 安全约定第 3 条走物理删除。
    @Delete("DELETE FROM jp_school_calendar_day WHERE id = #{id}")
    int deletePhysicallyById(@Param("id") Long id);
}
