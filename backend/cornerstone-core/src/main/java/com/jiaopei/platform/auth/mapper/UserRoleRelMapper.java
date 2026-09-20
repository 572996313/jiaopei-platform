package com.jiaopei.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiaopei.platform.auth.domain.UserRoleRelEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface UserRoleRelMapper extends BaseMapper<UserRoleRelEntity> {

    @Delete("DELETE FROM jp_user_role_rel WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}
