package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.ClassTeacherEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ClassTeacherMapper extends BaseMapper<ClassTeacherEntity> {

    /**
     * 任课关系是纯关联行，没有历史留存价值：走物理删除，让
     * uk_jp_class_teacher_period(class_id, teacher_id, teacher_role, joined_at) 彻底腾出键位。
     * 该唯一键含可空 joined_at，做「(键, deleted)」或守卫列都不如直接删行干净；
     * 表上没有子外键引用，物理删除不会触发 1451。
     */
    @Delete("DELETE FROM jp_class_teacher WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
