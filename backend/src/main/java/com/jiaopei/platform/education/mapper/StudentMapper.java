package com.jiaopei.platform.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiaopei.platform.education.domain.entity.StudentEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface StudentMapper extends BaseMapper<StudentEntity> {

    /**
     * 用户注销时清除其档案归属(user_id 置空)。档案本身保留(可能关联班级/考勤),仅解除归属。
     */
    @Update("UPDATE jp_student SET user_id = NULL WHERE user_id = #{userId}")
    int releaseOwnerByUserId(@Param("userId") Long userId);
}
