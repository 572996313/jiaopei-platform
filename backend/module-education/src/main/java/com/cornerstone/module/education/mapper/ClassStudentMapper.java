package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.ClassStudentEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface ClassStudentMapper extends BaseMapper<ClassStudentEntity> {

    // uk_jp_class_student (class_id, student_id) 不含 deleted 列，逻辑删除会持续占用唯一键，
    // 导致学员移出后无法再次入班，因此移除走物理删除
    @Delete("DELETE FROM jp_class_student WHERE id = #{id}")
    int deletePhysicallyById(@Param("id") Long id);
}
