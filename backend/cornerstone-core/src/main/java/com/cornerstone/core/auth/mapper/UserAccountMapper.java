package com.cornerstone.core.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cornerstone.core.auth.domain.UserAccountEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {

    /**
     * 物理删除用户行。自助注销需真正删行,否则逻辑删除(deleted=1)的行会占用
     * username 唯一键,导致同一微信注销后无法重新注册。
     */
    @Delete("DELETE FROM sys_user_account WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}