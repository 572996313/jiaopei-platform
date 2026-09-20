package com.jiaopei.platform.education.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiaopei.platform.education.domain.entity.UserStudentRelEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface UserStudentRelMapper extends BaseMapper<UserStudentRelEntity> {

    /**
     * 物理删除某用户与某学员的绑定关系。解绑必须真删行,否则逻辑删除(deleted=1)的行仍占用
     * (user_id, student_id) 唯一键,导致解绑后无法重新绑定。
     */
    @Delete("DELETE FROM jp_user_student_rel WHERE user_id = #{userId} AND student_id = #{studentId}")
    int physicalDelete(@Param("userId") Long userId, @Param("studentId") Long studentId);

    /** 用户注销时物理删除其全部学员关联 */
    @Delete("DELETE FROM jp_user_student_rel WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}