package com.cornerstone.core.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.auth.domain.UserIdentityEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface UserIdentityMapper extends BaseMapper<UserIdentityEntity> {

    /**
     * 物理删除某账号的全部第三方绑定。账号注销时必须清掉，
     * 否则指向 sys_user_account 的外键会让物理删账号直接报 1451。
     */
    @Delete("DELETE FROM sys_user_identity WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);

    /** 解绑走物理删除：墓碑会占住 (渠道,应用,openId,账号) 唯一键，导致解绑后无法重新绑定。 */
    @Delete("DELETE FROM sys_user_identity WHERE id = #{id} AND user_id = #{userId}")
    int physicalDeleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
