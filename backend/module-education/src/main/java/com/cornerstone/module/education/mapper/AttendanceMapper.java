package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.AttendanceEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface AttendanceMapper extends BaseMapper<AttendanceEntity> {

    /**
     * 撤销点名用的物理删除。软删不行：表上的唯一键是 (class_id, student_id, attendance_date)、不含 deleted，
     * 留一条已删记录会让同一个班同一天再也点不进这个名字，报"该学员在当天已存在考勤记录"。
     */
    @Delete("DELETE FROM jp_attendance WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
