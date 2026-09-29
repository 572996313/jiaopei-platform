package com.cornerstone.module.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.module.organization.domain.entity.UserCampusPrefEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface UserCampusPrefMapper extends BaseMapper<UserCampusPrefEntity> {

    /**
     * 物理删除某账号的校区偏好行。user_id 有外键指向 sys_user_account，
     * 账号注销前必须清掉，否则删账号被 1451 拒掉。
     */
    @Delete("DELETE FROM jp_user_campus_pref WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}
