package com.cornerstone.module.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.education.domain.entity.StudentEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface StudentMapper extends BaseMapper<StudentEntity> {

    /**
     * 用户注销时清除其档案归属(user_id 置空)。档案本身保留(可能关联班级/考勤),仅解除归属。
     */
    @Update("UPDATE jp_student SET user_id = NULL WHERE user_id = #{userId}")
    int releaseOwnerByUserId(@Param("userId") Long userId);

    /**
     * 软删档案并在同一条语句里解绑。
     *
     * <p>不能只靠 {@code deleteById}:uk_jp_student_user_id 是全表唯一键,软删行若留着 user_id,
     * 这个账号就被一条看不见的档案永久占住,再建新档案或绑定其他学员会撞 1062,
     * 而页面上查不到占着它的那行。与教师侧 {@code TeacherMapper#softDeleteAndUnlink} 同一口径。</p>
     */
    @Update("UPDATE jp_student SET user_id = NULL, deleted = 1 WHERE id = #{id} AND deleted = 0")
    int softDeleteAndUnlink(@Param("id") Long id);
}
